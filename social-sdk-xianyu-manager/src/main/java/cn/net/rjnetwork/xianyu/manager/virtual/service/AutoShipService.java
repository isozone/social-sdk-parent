package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.XianyuOrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.ship.service.DeliveryRuleEngine;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.DeliveryLogMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.DeliveryLog;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自动发货主链路 —— A8。
 * <p>把支付事件 → 卡券匹配 → 规则评估 → 延迟 → 发送 → 落库 完整串起来，
 * 对标参考项目 ship_order_service.py 的主链路。原 VirtualShipService 只做 task 调度壳，
 * 本服务承载发货决策与执行，避免原 processShipTask 单卡券/无规则/无落库的缺陷。</p>
 *
 * <p>链路顺序：</p>
 * <ol>
 *   <li>拉支付成功订单（status=TRADE_PAID 或 task 关联的订单）；</li>
 *   <li>A7 规则评估：调 DeliveryRuleEngine.shouldBlock，
 *       BLOCKED → 跳过+记+通知；NOTIFY_ONLY → 发但推通知；DELAY → 写下次执行时间；</li>
 *   <li>A6 多卡券匹配：按 card_item_relation.priority 升序拿首个 AVAILABLE 的 ship_card，
 *       拿不到则 SKIPPED+记「无可用卡券」触发 A9 补发；</li>
 *   <li>延迟窗口：若 ship_config 配置了 delaySeconds，写 task.nextRunAt 延后执行；</li>
 *   <li>发送：调 MessageService.sendMessage 把卡券内容发给买家；</li>
 *   <li>落库：写 DeliveryLog 审计 + 标 ship_card USED + 标 task SUCCESS。</li>
 * </ol>
 *
 * <p>批次日志复用 B9 BatchJobService（job_type=auto_ship）。</p>
 */
@Service
public class AutoShipService {

    private static final Logger log = LoggerFactory.getLogger(AutoShipService.class);
    private static final String JOB_TYPE = "auto_ship";

    private final XianyuOrderMapper orderMapper;
    private final ShipCardMapper shipCardMapper;
    private final CardItemRelationMapper relationMapper;
    private final DeliveryLogMapper deliveryLogMapper;
    private final VirtualShipTaskMapper taskMapper;
    private final AccountMapper accountMapper;
    private final DeliveryRuleEngine ruleEngine;
    private final MessageService messageService;
    private final BatchJobService batchJobService;
    private final ApplicationEventPublisher eventPublisher;

    public AutoShipService(XianyuOrderMapper orderMapper,
                           ShipCardMapper shipCardMapper,
                           CardItemRelationMapper relationMapper,
                           DeliveryLogMapper deliveryLogMapper,
                           VirtualShipTaskMapper taskMapper,
                           AccountMapper accountMapper,
                           DeliveryRuleEngine ruleEngine,
                           MessageService messageService,
                           BatchJobService batchJobService,
                           ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.shipCardMapper = shipCardMapper;
        this.relationMapper = relationMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.taskMapper = taskMapper;
        this.accountMapper = accountMapper;
        this.ruleEngine = ruleEngine;
        this.messageService = messageService;
        this.batchJobService = batchJobService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理单个发货任务 —— A8 主链路入口。
     * 由 VirtualShipService.scanAndShip 对每个 PENDING task 调一次，也可管理端手动触发。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processShipTask(VirtualShipTask task) {
        long t0 = System.currentTimeMillis();
        task.setStatus("PROCESSING");
        taskMapper.updateById(task);

        DeliveryLog audit = new DeliveryLog();
        audit.setAccountId(task.getAccountId());
        audit.setOrderId(task.getOrderId());
        audit.setProductId(task.getProductId());
        audit.setBatchJobId(null);
        audit.setShippedAt(LocalDateTime.now());

        try {
            XianyuOrder order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                finalizeFail(task, audit, "FAILED", "订单不存在");
                return;
            }
            audit.setBuyerId(order.getBuyerId());

            // 1. A7 规则评估
            DeliveryRuleEngine.DeliveryContext ctx = new DeliveryRuleEngine.DeliveryContext(
                    task.getAccountId(), parseLong(order.getBuyerId()), task.getProductId(),
                    order.getPrice() == null ? null : order.getPrice().doubleValue(),
                    order.getBuyerRegion());
            DeliveryRuleEngine.BlockDecision decision = ruleEngine.shouldBlock(ctx);
            audit.setRuleDecision(decision.block ? decision.action : "PASS");
            audit.setHitRuleName(decision.ruleName);
            if (decision.block && "BLOCK".equals(decision.action)) {
                finalizeSkip(task, audit, "规则拦截：" + decision.reason);
                publishNotify(task, order, "AUTO_SHIP_BLOCKED", decision.reason);
                return;
            }
            if ("DELAY".equals(decision.action)) {
                task.setStatus("PENDING");
                task.setNextRunAt(LocalDateTime.now().plusMinutes(30));
                taskMapper.updateById(task);
                audit.setStatus("DELAYED");
                audit.setFailureReason(decision.reason);
                deliveryLogMapper.insert(audit);
                return;
            }
            // NOTIFY_ONLY 继续发货但推通知
            if (decision.block && "NOTIFY_ONLY".equals(decision.action)) {
                publishNotify(task, order, "AUTO_SHIP_NOTIFY_ONLY", decision.reason);
            }

            // 2. A6 多卡券匹配：按 priority 升序拿首个 AVAILABLE
            List<CardItemRelation> rels = relationMapper.selectEnabledByProductId(task.getProductId());
            ShipCard matched = null;
            for (CardItemRelation rel : rels) {
                ShipCard card = shipCardMapper.selectById(rel.getCardId());
                if (card != null && "AVAILABLE".equals(card.getStatus())) {
                    matched = card;
                    break;
                }
            }
            if (matched == null) {
                finalizeSkip(task, audit, "无可用卡券，触发 A9 补发");
                // 推通知让运营补卡券池；A9 补发任务也会扫到本 task 重试
                publishNotify(task, order, "AUTO_SHIP_NO_CARD", "productId=" + task.getProductId() + " 无可用卡券");
                return;
            }
            audit.setShipCardId(matched.getId());
            audit.setDeliverContent(buildDeliverContent(matched));

            // 3. 延迟窗口（task.delaySeconds 非空时延后发送，防被风控盯上）
            if (task.getDelaySeconds() != null && task.getDelaySeconds() > 0
                    && (task.getNextRunAt() == null || task.getNextRunAt().isAfter(LocalDateTime.now()))) {
                task.setStatus("PENDING");
                if (task.getNextRunAt() == null) {
                    task.setNextRunAt(LocalDateTime.now().plusSeconds(task.getDelaySeconds()));
                }
                taskMapper.updateById(task);
                audit.setStatus("DELAYED");
                audit.setFailureReason("延迟 " + task.getDelaySeconds() + "s 后发");
                deliveryLogMapper.insert(audit);
                return;
            }

            // 4. 发送：调 MessageService 把卡券内容发给买家
            XianyuAccount account = accountMapper.selectById(task.getAccountId());
            if (account == null) {
                finalizeFail(task, audit, "FAILED", "账号不存在");
                return;
            }
            String deliverText = buildDeliverContent(matched);
            try {
                messageService.sendText(account, order.getSessionId(), deliverText);
            } catch (Exception e) {
                finalizeFail(task, audit, "FAILED", "发送失败：" + e.getMessage());
                return;
            }

            // 5. 落库 + 标 USED + 标 task SUCCESS
            matched.setStatus("USED");
            matched.setUsedOrderId(task.getOrderId());
            matched.setUsedAt(LocalDateTime.now());
            shipCardMapper.updateById(matched);

            audit.setStatus("SUCCESS");
            audit.setDurationMs(System.currentTimeMillis() - t0);
            deliveryLogMapper.insert(audit);

            task.setStatus("SUCCESS");
            task.setShippedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            log.info("[A8] order {} shipped via card {}", task.getOrderId(), matched.getId());
        } catch (Exception e) {
            finalizeFail(task, audit, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[A8] processShipTask order {} failed: {}", task.getOrderId(), e.getMessage());
        }
    }

    /** 拼卡券发货文本（四类型各自格式）。 */
    private String buildDeliverContent(ShipCard card) {
        return switch (Optional.ofNullable(card.getCardType()).orElse("CARD")) {
            case "ACCOUNT" -> "账号：" + nullSafe(card.getCardCode()) + "\n密码：" + nullSafe(card.getCardPassword())
                    + (card.getExtra() == null ? "" : "\n服务器：" + card.getExtra());
            case "LINK_QRCODE" -> nullSafe(card.getContent());
            case "PLAIN_TEXT" -> nullSafe(card.getContent());
            default -> "卡号：" + nullSafe(card.getCardCode()) + "\n卡密：" + nullSafe(card.getCardPassword());
        };
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private void finalizeFail(VirtualShipTask task, DeliveryLog audit, String status, String reason) {
        audit.setStatus(status);
        audit.setFailureReason(reason);
        audit.setDurationMs(0L);
        deliveryLogMapper.insert(audit);
        task.setStatus("FAILED");
        task.setLastError(reason);
        taskMapper.updateById(task);
    }

    private void finalizeSkip(VirtualShipTask task, DeliveryLog audit, String reason) {
        audit.setStatus("SKIPPED");
        audit.setFailureReason(reason);
        audit.setDurationMs(0L);
        deliveryLogMapper.insert(audit);
        task.setStatus("SKIPPED");
        task.setLastError(reason);
        taskMapper.updateById(task);
    }

    private void publishNotify(VirtualShipTask task, XianyuOrder order, String type, String reason) {
        try {
            eventPublisher.publishEvent(new cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent(
                    type, task.getAccountId(),
                    Optional.ofNullable(order).map(o -> o.getBuyerNick()).orElse(""),
                    java.util.Map.of("orderId", String.valueOf(task.getOrderId()),
                            "productId", String.valueOf(task.getProductId()),
                            "reason", reason == null ? "" : reason)));
        } catch (Exception ignored) {}
    }

    /** 批次入口：扫所有 PENDING task 跑一遍，给 VirtualShipService.scanAndShip 调。 */
    public Long runBatch(String triggerSource) {
        List<VirtualShipTask> pendings = taskMapper.selectList(new LambdaQueryWrapper<VirtualShipTask>()
                .eq(VirtualShipTask::getStatus, "PENDING")
                .and(w -> w.isNull(VirtualShipTask::getNextRunAt)
                        .or().le(VirtualShipTask::getNextRunAt, LocalDateTime.now())));
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, pendings.size());
        int success = 0, failed = 0, skipped = 0;
        for (VirtualShipTask t : pendings) {
            long t0 = System.currentTimeMillis();
            try {
                processShipTask(t);
                String st = t.getStatus();
                if ("SUCCESS".equals(st)) {
                    success++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "SUCCESS", System.currentTimeMillis() - t0, null, null);
                } else if ("SKIPPED".equals(st) || "DELAYED".equals(st)) {
                    skipped++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "SKIPPED", System.currentTimeMillis() - t0,
                            t.getLastError(), null);
                } else {
                    failed++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "FAILED", System.currentTimeMillis() - t0,
                            t.getLastError(), null);
                }
            } catch (Exception e) {
                failed++;
                batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                        "order#" + t.getOrderId(), "FAILED", System.currentTimeMillis() - t0,
                        e.getMessage(), null);
            }
        }
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", pendings.size(), success, failed, skipped));
        return job.getId();
    }

    /** 供消息同步链路在收到支付事件时直接触发：建 task → 入队 → 立即跑。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaySuccess(Long accountId, Long orderId, Long productId, String sessionId) {
        VirtualShipTask task = new VirtualShipTask();
        task.setAccountId(accountId);
        task.setOrderId(orderId);
        task.setProductId(productId);
        task.setSessionId(sessionId);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("[A8] pay success event → enqueue ship task orderId={} productId={}", orderId, productId);
        // 立即跑一次（链路内含延迟/规则，不会无脑发）
        processShipTask(task);
    }

    /** 兜底用：扫订单表里 TRADE_PAID 但还没建 task 的，补建入队。由 scanAndShip 调。 */
    public int enqueuePaidOrders() {
        List<XianyuOrder> paid = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getStatus, "TRADE_PAID"));
        int enqueued = 0;
        for (XianyuOrder o : paid) {
            // 已有同订单 PENDING/SUCCESS task 则跳过
            Long exists = taskMapper.selectCount(new LambdaQueryWrapper<VirtualShipTask>()
                    .eq(VirtualShipTask::getOrderId, o.getId())
                    .in(VirtualShipTask::getStatus, "PENDING", "PROCESSING", "SUCCESS"));
            if (exists != null && exists > 0) continue;
            VirtualShipTask task = new VirtualShipTask();
            task.setAccountId(o.getAccountId());
            task.setOrderId(o.getId());
            task.setProductId(o.getProductId() == null ? null : o.getProductId().longValue());
            task.setSessionId(o.getSessionId());
            task.setStatus("PENDING");
            task.setCreatedAt(LocalDateTime.now());
            taskMapper.insert(task);
            enqueued++;
        }
        return enqueued;
    }
}
