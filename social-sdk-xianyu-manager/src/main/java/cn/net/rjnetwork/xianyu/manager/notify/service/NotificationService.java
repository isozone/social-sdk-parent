package cn.net.rjnetwork.xianyu.manager.notify.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.message.websocket.MessageBroadcaster;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyScenario;
import cn.net.rjnetwork.xianyu.manager.notify.TemplateRenderer;
import cn.net.rjnetwork.xianyu.manager.notify.adapter.ChannelAdapter;
import cn.net.rjnetwork.xianyu.manager.notify.model.*;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.*;
import cn.net.rjnetwork.xianyu.manager.notify.service.SendRateLimiter;
import cn.net.rjnetwork.xianyu.manager.notify.service.RetryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通知编排核心。监听业务发布的 NotifyEvent，按订阅规则分发到各通道（异步），
 * 写投递日志，并落站内收件箱（NotifyMessage）。带场景级去重冷却，防骚扰。
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final NotifyChannelMapper channelMapper;
    private final NotifyTemplateMapper templateMapper;
    private final NotifySubscriptionMapper subscriptionMapper;
    private final NotifyLogMapper logMapper;
    private final NotifyMessageMapper messageMapper;
    private final CryptoUtil cryptoUtil;
    private final MessageBroadcaster broadcaster;
    private final List<ChannelAdapter> adapters;
    private final SendRateLimiter rateLimiter;
    private final RetryService retryService;

    @Value("${notify.rate-limit-retry-delay-seconds:30}")
    private int rateLimitRetryDelay;

    /** 去重缓存：scenario::accountId -> 上次发送时间；冷却内不重复发 */
    private final Cache<String, Long> dedup = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .build();

    // ==================== NEW_MESSAGE 聚合缓冲 ====================
    //
    // 站内消息推送频繁的根因：闲鱼消息每 30 秒轮询一次，每条新买家消息都会立即触发
    // NEW_MESSAGE 通知。当买家连续发多条消息（或多个买家同时发），30 秒冷却仍然刷屏。
    //
    // 方案：NEW_MESSAGE 不再立即推送，而是按账号聚合成摘要，冷却时间到后再批量刷出。
    // 聚合维度：accountId（同账号多条消息合并为一条摘要）。
    // 冷却时间：notify.new-message.cooldown-seconds（默认 300 秒 / 5 分钟）。
    // 刷出时机：① 聚合缓冲达到 maxBufferSize 条立即刷出；② 定时任务每分钟扫描超时缓冲。
    //
    // 聚合后的摘要内容示例：
    //   标题：账号 xxx 收到 3 条新消息
    //   正文：账号 xxx 收到来自买家的消息：
    //        - 买家A: 在吗
    //        - 买家B: 这个还有吗
    //        - 买家A: 多少钱

    /** NEW_MESSAGE 聚合冷却时间（秒），默认 5 分钟 */
    @Value("${notify.new-message.cooldown-seconds:300}")
    private int newMessageCooldownSeconds;

    /** NEW_MESSAGE 聚合缓冲单账号最大条数，达到立即刷出 */
    @Value("${notify.new-message.max-buffer-size:10}")
    private int newMessageMaxBufferSize;

    /** NEW_MESSAGE 聚合缓冲：accountId -> 待刷出的消息列表 */
    private final ConcurrentMap<Long, List<NotifyEvent>> newMessageBuffer = new ConcurrentHashMap<>();

    /** NEW_MESSAGE 聚合缓冲：accountId -> 首条消息进入缓冲的时间戳 */
    private final ConcurrentMap<Long, Long> newMessageBufferSince = new ConcurrentHashMap<>();

    public NotificationService(NotifyChannelMapper channelMapper,
                              NotifyTemplateMapper templateMapper,
                              NotifySubscriptionMapper subscriptionMapper,
                              NotifyLogMapper logMapper,
                              NotifyMessageMapper messageMapper,
                              CryptoUtil cryptoUtil,
                              MessageBroadcaster broadcaster,
                              List<ChannelAdapter> adapters,
                              SendRateLimiter rateLimiter,
                              RetryService retryService) {
        this.channelMapper = channelMapper;
        this.templateMapper = templateMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.logMapper = logMapper;
        this.messageMapper = messageMapper;
        this.cryptoUtil = cryptoUtil;
        this.broadcaster = broadcaster;
        this.adapters = adapters;
        this.rateLimiter = rateLimiter;
        this.retryService = retryService;
    }

    @Async
    @EventListener
    public void onEvent(NotifyEvent event) {
        NotifyScenario scenario = NotifyScenario.fromName(event.getScenario());
        if (scenario == null) {
            logger.warn("未知通知场景：{}", event.getScenario());
            return;
        }

        // NEW_MESSAGE 走聚合缓冲，不立即推送
        if ("NEW_MESSAGE".equals(event.getScenario())) {
            bufferNewMessage(event);
            return;
        }

        // 去重冷却
        String dedupKey = event.getScenario() + "::" + (event.getAccountId() != null ? event.getAccountId() : "global");
        Long last = dedup.getIfPresent(dedupKey);
        long now = System.currentTimeMillis();
        if (last != null && (now - last) < scenario.getCooldownSeconds() * 1000L) {
            logger.debug("场景 {} 冷却中，跳过", event.getScenario());
            return;
        }
        dedup.put(dedupKey, now);

        // 渲染模板
        NotifyTemplate tpl = templateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifyTemplate>()
                        .eq(NotifyTemplate::getScenario, event.getScenario())
                        .eq(NotifyTemplate::getEnabled, true));
        String title = TemplateRenderer.render(
                tpl != null && tpl.getTitleTpl() != null ? tpl.getTitleTpl() : scenario.getDefaultTitle(),
                event.getVars());
        String body = TemplateRenderer.render(
                tpl != null && tpl.getBodyTpl() != null ? tpl.getBodyTpl() : scenario.getDefaultBody(),
                event.getVars());

        // 落站内收件箱 + 实时广播 + 按订阅分发外部通道
        saveInApp(event, title, body);
        try {
            Map<String, Object> broadcastPayload = new LinkedHashMap<>();
            broadcastPayload.put("type", "notification");
            broadcastPayload.put("scenario", event.getScenario());
            broadcastPayload.put("title", title);
            broadcastPayload.put("body", body);
            broadcastPayload.put("accountId", event.getAccountId());
            broadcaster.broadcastAll(mapper.writeValueAsString(broadcastPayload));
            logger.info("[NotifyLoop] IN_APP_BROADCAST scenario={} accountId={}", event.getScenario(), event.getAccountId());
        } catch (Exception e) {
            logger.warn("[NotifyLoop] IN_APP_BROADCAST_FAILED scenario={} accountId={} error={}",
                    event.getScenario(), event.getAccountId(), e.getMessage());
        }
        dispatchExternal(event, title, body);
    }

    // ==================== NEW_MESSAGE 聚合缓冲实现 ====================

    /**
     * 把单条 NEW_MESSAGE 事件加入账号聚合缓冲。
     * <p>聚合策略：</p>
     * <ul>
     *   <li>缓冲为空 → 记录首条时间戳，加入缓冲</li>
     *   <li>缓冲未满且未超时 → 加入缓冲，等定时任务刷出</li>
     *   <li>缓冲达到 maxBufferSize → 立即刷出（防止突发消息堆积太久）</li>
     * </ul>
     */
    private void bufferNewMessage(NotifyEvent event) {
        Long accountId = event.getAccountId();
        if (accountId == null) {
            // 没有账号维度的 NEW_MESSAGE 直接发（兼容旧调用）
            dispatchImmediate(event);
            return;
        }

        List<NotifyEvent> buffer = newMessageBuffer.computeIfAbsent(accountId, k -> new ArrayList<>());
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                newMessageBufferSince.put(accountId, System.currentTimeMillis());
            }
            buffer.add(event);
            logger.debug("[NotifyLoop] NEW_MESSAGE_BUFFERED accountId={} bufferSize={}",
                    accountId, buffer.size());

            // 达到单账号缓冲上限 → 立即刷出
            if (buffer.size() >= newMessageMaxBufferSize) {
                flushAccountBuffer(accountId);
            }
        }
    }

    /**
     * 定时扫描聚合缓冲，把超时（达到 cooldown）的缓冲刷出。
     * <p>每分钟执行一次，扫描所有有缓冲的账号，
     * 若首条消息进入缓冲时间 + cooldown 已过，则刷出。</p>
     */
    @Scheduled(fixedDelayString = "${notify.new-message.flush-interval-ms:60000}")
    public void flushExpiredBuffers() {
        if (newMessageBuffer.isEmpty()) return;
        long now = System.currentTimeMillis();
        long cooldownMs = newMessageCooldownSeconds * 1000L;

        // 快照账号列表，避免遍历时 flushAccountBuffer 改变 map
        List<Long> accountIds = new ArrayList<>(newMessageBuffer.keySet());
        for (Long accountId : accountIds) {
            Long since = newMessageBufferSince.get(accountId);
            if (since == null) {
                // 缓冲已被清空但 since 残留 → 清理
                newMessageBufferSince.remove(accountId);
                continue;
            }
            if ((now - since) >= cooldownMs) {
                flushAccountBuffer(accountId);
            }
        }
    }

    /**
     * 刷出指定账号的聚合缓冲，合成一条摘要通知后走正常分发链路。
     */
    private void flushAccountBuffer(Long accountId) {
        List<NotifyEvent> buffer = newMessageBuffer.remove(accountId);
        newMessageBufferSince.remove(accountId);
        if (buffer == null || buffer.isEmpty()) return;

        // 取第一条事件作为基准（accountId、accountName 一致）
        NotifyEvent base = buffer.get(0);
        int total = buffer.size();

        // 合成聚合摘要标题/正文
        String title = "账号 " + base.getAccountName() + " 收到 " + total + " 条新消息";
        StringBuilder bodySb = new StringBuilder();
        bodySb.append("账号 ").append(base.getAccountName()).append(" 收到来自买家的消息：\n");
        int shown = 0;
        for (NotifyEvent ev : buffer) {
            if (shown >= 5) {
                bodySb.append("... 等 ").append(total - 5).append(" 条\n");
                break;
            }
            Map<String, Object> vars = ev.getVars();
            String senderName = vars.getOrDefault("senderName", "").toString();
            String content = vars.getOrDefault("content", "").toString();
            // 截断过长内容
            if (content.length() > 50) content = content.substring(0, 50) + "...";
            bodySb.append("- ").append(senderName.isEmpty() ? "买家" : senderName)
                  .append(": ").append(content).append("\n");
            shown++;
        }

        // 构造聚合 NotifyEvent，走正常分发链路
        Map<String, Object> aggVars = new LinkedHashMap<>();
        aggVars.put("accountName", base.getAccountName());
        aggVars.put("messageCount", total);
        aggVars.put("summary", bodySb.toString());
        NotifyEvent aggEvent = new NotifyEvent("NEW_MESSAGE", accountId, base.getAccountName(), aggVars);

        // 聚合后直接走立即分发（绕过 dedup，因为聚合本身就是频控）
        dispatchAggregated(aggEvent, title, bodySb.toString());
        logger.info("[NotifyLoop] NEW_MESSAGE_FLUSHED accountId={} count={}", accountId, total);
    }

    /**
     * 聚合摘要的直接分发（不经过 dedup 冷却，因为聚合本身就是频控）。
     * 落站内收件箱 + 实时广播 + 按订阅分发外部通道。
     */
    private void dispatchAggregated(NotifyEvent event, String title, String body) {
        // 落站内收件箱
        saveInApp(event, title, body);
        // 站内实时广播
        try {
            Map<String, Object> broadcastPayload = new LinkedHashMap<>();
            broadcastPayload.put("type", "notification");
            broadcastPayload.put("scenario", event.getScenario());
            broadcastPayload.put("title", title);
            broadcastPayload.put("body", body);
            broadcastPayload.put("accountId", event.getAccountId());
            broadcaster.broadcastAll(mapper.writeValueAsString(broadcastPayload));
            logger.info("[NotifyLoop] IN_APP_BROADCAST scenario={} accountId={}",
                    event.getScenario(), event.getAccountId());
        } catch (Exception e) {
            logger.warn("[NotifyLoop] IN_APP_BROADCAST_FAILED scenario={} accountId={} error={}",
                    event.getScenario(), event.getAccountId(), e.getMessage());
        }
        // 按订阅规则分发外部通道（复用原逻辑）
        dispatchExternal(event, title, body);
    }

    /**
     * 无账号维度的 NEW_MESSAGE 直接分发（兼容旧调用路径）。
     */
    private void dispatchImmediate(NotifyEvent event) {
        NotifyScenario scenario = NotifyScenario.fromName(event.getScenario());
        if (scenario == null) return;
        NotifyTemplate tpl = templateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifyTemplate>()
                        .eq(NotifyTemplate::getScenario, event.getScenario())
                        .eq(NotifyTemplate::getEnabled, true));
        String title = TemplateRenderer.render(
                tpl != null && tpl.getTitleTpl() != null ? tpl.getTitleTpl() : scenario.getDefaultTitle(),
                event.getVars());
        String body = TemplateRenderer.render(
                tpl != null && tpl.getBodyTpl() != null ? tpl.getBodyTpl() : scenario.getDefaultBody(),
                event.getVars());
        saveInApp(event, title, body);
        dispatchExternal(event, title, body);
    }

    /**
     * 按订阅规则分发外部通道（从 onEvent 抽取，聚合与立即分发共用）。
     */
    private void dispatchExternal(NotifyEvent event, String title, String body) {
        List<NotifySubscription> subs = subscriptionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifySubscription>()
                        .eq(NotifySubscription::getScenario, event.getScenario())
                        .eq(NotifySubscription::getEnabled, true));
        if (subs.isEmpty()) {
            logger.info("[NotifyLoop] EXTERNAL_SKIPPED_NO_SUBSCRIPTION scenario={} accountId={}",
                    event.getScenario(), event.getAccountId());
            return;
        }
        for (NotifySubscription sub : subs) {
            NotifyChannel channel = channelMapper.selectById(sub.getChannelId());
            if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) continue;
            if ("CUSTOM".equals(sub.getAccountScope())
                    && event.getAccountId() != null
                    && !accountInList(sub.getAccountIds(), event.getAccountId())) {
                continue;
            }
            String decrypted = cryptoUtil.decrypt(channel.getConfigJson());
            channel.setConfigJson(decrypted);
            List<String> recipients = resolveRecipients(sub, channel);

            int channelRate = 0;
            try {
                JsonNode cfgNode = mapper.readTree(channel.getConfigJson());
                if (cfgNode != null && cfgNode.has("rateLimitPerMinute")) {
                    channelRate = cfgNode.get("rateLimitPerMinute").asInt(0);
                }
            } catch (Exception ignored) {}
            if (!rateLimiter.tryAcquire(channel.getId(), channelRate)) {
                logger.warn("通道 {} 触发限频，转入重试队列", channel.getName());
                retryService.enqueue(event, channel, recipients, title, body, "触发限频，延迟重试", rateLimitRetryDelay);
                continue;
            }

            try {
                ChannelAdapter adapter = adapterFor(channel.getType());
                if (adapter == null) {
                    throw new IllegalStateException("无对应通道适配器： " + channel.getType());
                }
                adapter.send(channel, title, body, recipients, event.getVars());
                writeLog(event, channel, recipients, "SENT", null);
                logger.info("[NotifyLoop] EXTERNAL_SENT scenario={} channelId={} channelName={}",
                        event.getScenario(), channel.getId(), channel.getName());
            } catch (Exception e) {
                logger.warn("[NotifyLoop] EXTERNAL_FAILED scenario={} channelId={} error={}",
                        event.getScenario(), channel.getId(), e.getMessage(), e);
                writeLog(event, channel, recipients, "FAILED", e.getMessage());
                retryService.enqueue(event, channel, recipients, title, body, e.getMessage());
            }
        }
    }

    private void saveInApp(NotifyEvent event, String title, String body) {
        try {
            NotifyMessage msg = new NotifyMessage();
            msg.setAccountId(event.getAccountId());
            msg.setScenario(event.getScenario());
            msg.setTitle(title);
            msg.setContent(body);
            msg.setIsRead(false);
            msg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(msg);
            logger.info("[NotifyLoop] IN_APP_SAVED scenario={} accountId={} title={}",
                    event.getScenario(), event.getAccountId(), title);
        } catch (Exception e) {
            logger.error("保存站内通知失败", e);
        }
    }

    /**
     * 直接发送测试消息（供通道“测试”按钮调用）。config_json 应为已解密的明文。
     */
    public void sendTest(NotifyChannel channel, String title, String body) {
        if (channel == null || channel.getConfigJson() == null) {
            throw new IllegalArgumentException("通道配置为空");
        }
        ChannelAdapter adapter = adapterFor(channel.getType());
        if (adapter == null) throw new IllegalStateException("无对应通道适配器： " + channel.getType());
        try {
            adapter.send(channel, title, body, Collections.emptyList(), Map.of("body", body, "title", title));
            writeLog(new NotifyEvent("TEST", null, null, Map.of()), channel, List.of(), "SENT", null);
        } catch (Exception e) {
            writeLog(new NotifyEvent("TEST", null, null, Map.of()), channel, List.of(), "FAILED", e.getMessage());
            throw new IllegalStateException("发送失败： " + e.getMessage(), e);
        }
    }

    /**
     * 经指定通道直接发送一条消息（供每日摘要等合成场景调用）。
     * config_json 自动解密，发送结果写入投递日志。
     */
    public void dispatchViaChannel(Long channelId, List<String> recipients, String title, String body) {
        NotifyChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new IllegalStateException("通道不存在或已禁用");
        }
        String decrypted = cryptoUtil.decrypt(channel.getConfigJson());
        channel.setConfigJson(decrypted);
        try {
            ChannelAdapter adapter = adapterFor(channel.getType());
            if (adapter == null) {
                throw new IllegalStateException("无对应通道适配器： " + channel.getType());
            }
            adapter.send(channel, title, body, recipients == null ? Collections.emptyList() : recipients, Map.of("body", body, "title", title));
            writeLog(new NotifyEvent("DIGEST", null, null, Map.of()), channel,
                    recipients == null ? Collections.emptyList() : recipients, "SENT", null);
        } catch (Exception e) {
            writeLog(new NotifyEvent("DIGEST", null, null, Map.of()), channel,
                    recipients == null ? Collections.emptyList() : recipients, "FAILED", e.getMessage());
            throw new IllegalStateException("摘要发送失败： " + e.getMessage(), e);
        }
    }

    private List<String> resolveRecipients(NotifySubscription sub, NotifyChannel channel) {
        if ("CUSTOM".equals(sub.getRecipientScope()) && sub.getRecipients() != null && !sub.getRecipients().isBlank()) {
            return Arrays.asList(sub.getRecipients().split("[,;\\s]+"));
        }
        return Collections.emptyList(); // EMAIL 会回退到通道 defaultTo；WEBHOOK 忽略
    }

    private boolean accountInList(String json, Long accountId) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode n : arr) if (n.asLong() == accountId) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private ChannelAdapter adapterFor(String type) {
        return adapters.stream().filter(a -> a.type().equals(type)).findFirst().orElse(null);
    }

    private void writeLog(NotifyEvent event, NotifyChannel channel, List<String> recipients, String status, String error) {
        try {
            NotifyLog log = new NotifyLog();
            log.setScenario(event.getScenario());
            log.setChannelId(channel.getId());
            log.setChannelType(channel.getType());
            log.setRecipient(recipients.isEmpty() ? "(通道默认)" : String.join(",", recipients));
            log.setStatus(status);
            log.setPayload(channel.getType() + " -> " + channel.getName());
            log.setError(error);
            log.setCreatedAt(LocalDateTime.now());
            log.setSentAt(status.equals("SENT") ? LocalDateTime.now() : null);
            logMapper.insert(log);
            logger.info("[NotifyLoop] DELIVERY_LOG_WRITTEN scenario={} channelId={} channelType={} status={} recipient={}",
                    event.getScenario(), channel.getId(), channel.getType(), status, log.getRecipient());
        } catch (Exception ignored) {}
    }
}
