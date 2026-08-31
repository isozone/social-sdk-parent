package cn.net.rjnetwork.xianyu.manager.account.renew.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.CookieRefreshScheduleMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledCookiesRefreshLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.CookieRefreshSchedule;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledCookiesRefreshLog;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cookie 浏览器刷新服务 —— A1。
 * <p>核心链路：检测当前 Cookie 是否失效 → 启动/复用账号独占 Chrome 容器 →
 * 导航到 goofish.com → 通过 CDP Network.getCookies 提取新 Cookie → 写回加密 Cookie →
 * 记录批次日志 + 明细（复用 B9 BatchJobService）→ 失败触发熔断/通知。</p>
 *
 * <p>与 A2（API 续期）形成双通道：A2 优先（轻量），失败降级到 A1（浏览器刷新）。
 * 与 A3（登录续期）协作：Cookie 彻底失效时 A1 也失败 → 触发 A3 重新登录流程。</p>
 *
 * <p>刷新策略 onlyOnExpired=1（默认）：仅在健康检测判定 Cookie 失效时才刷新，
 * 避免无谓浏览器启动；onlyOnExpired=0 则定时强制刷新（用于长期保活场景）。</p>
 */
@Service
public class CookieRenewService {

    private static final Logger log = LoggerFactory.getLogger(CookieRenewService.class);
    private static final String JOB_TYPE = "cookies_refresh";
    private static final String IM_PAGE_URL = "https://www.goofish.com/";

    /** goofish/taobao 生态相关域名（提取 cookie 时只保留这些，过滤掉无关站点的 cookie 噪声）。 */
    private static final List<String> RELATED_DOMAINS = List.of(
            "goofish.com", "taobao.com", "tbcdn.cn", "alicdn.com", "aliyun.com", "alipay.com", "aliimg.com");
    private static final OkHttpClient CDP_HTTP = new OkHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccountMapper accountMapper;
    private final CookieRefreshScheduleMapper scheduleMapper;
    private final ScheduledCookiesRefreshLogMapper logMapper;
    private final AccountService accountService;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApiCookieRenewService apiCookieRenewService;

    public CookieRenewService(AccountMapper accountMapper,
                              CookieRefreshScheduleMapper scheduleMapper,
                              ScheduledCookiesRefreshLogMapper logMapper,
                              AccountService accountService,
                              CircuitBreakerService circuitBreaker,
                              BatchJobService batchJobService,
                              ApplicationEventPublisher eventPublisher,
                              ApiCookieRenewService apiCookieRenewService) {
        this.accountMapper = accountMapper;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
        this.accountService = accountService;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
        this.eventPublisher = eventPublisher;
        this.apiCookieRenewService = apiCookieRenewService;
    }

    // ==================== 计划管理 ====================

    /** 为账号创建或更新刷新计划；intervalMinutes 默认 720（12 小时），onlyOnExpired 默认 1。 */
    @Transactional
    public CookieRefreshSchedule upsertSchedule(Long accountId, Integer intervalMinutes, Integer onlyOnExpired) {
        CookieRefreshSchedule schedule = scheduleMapper.selectByAccountId(accountId);
        boolean isNew = schedule == null;
        if (isNew) {
            schedule = new CookieRefreshSchedule();
            schedule.setAccountId(accountId);
            schedule.setEnabled(1);
        }
        if (intervalMinutes != null && intervalMinutes > 0) schedule.setIntervalMinutes(intervalMinutes);
        if (schedule.getIntervalMinutes() == null) schedule.setIntervalMinutes(720);
        if (onlyOnExpired != null) schedule.setOnlyOnExpired(onlyOnExpired);
        if (schedule.getOnlyOnExpired() == null) schedule.setOnlyOnExpired(1);
        schedule.setNextRunAt(LocalDateTime.now().plusMinutes(schedule.getIntervalMinutes()));
        if (isNew) scheduleMapper.insert(schedule);
        else scheduleMapper.updateById(schedule);
        return schedule;
    }

    /** 启停账号刷新计划。 */
    @Transactional
    public boolean toggleSchedule(Long accountId, boolean enabled) {
        CookieRefreshSchedule schedule = scheduleMapper.selectByAccountId(accountId);
        if (schedule == null) return false;
        schedule.setEnabled(enabled ? 1 : 0);
        scheduleMapper.updateById(schedule);
        return true;
    }

    // ==================== 批次执行入口 ====================

    /**
     * 执行一次 Cookie 浏览器刷新批次 —— 由 CookiesRefreshTask 定时调用，也可管理端手动触发。
     * @param triggerSource SCHEDULER / MANUAL / SYSTEM
     * @param onlyExpiredOnly true=仅刷新健康检测失效的账号（onlyOnExpired=1 的计划）
     * @return 批次日志主键
     */
    public Long runBatch(String triggerSource, boolean onlyExpiredOnly) {
        // 1. 收集待刷新账号：enabled=1 且（nextRunAt 已到 或 onlyOnExpired=1 且已失效）
        List<XianyuAccount> candidates = collectCandidates(onlyExpiredOnly);
        ScheduledCookiesRefreshLog batch = startLog(triggerSource, candidates.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (XianyuAccount account : candidates) {
            long t0 = System.currentTimeMillis();
            try {
                RenewResult r = renewOne(account);
                if (r == RenewResult.SUCCESS) {
                    success++;
                    batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                            account.getAccountName(), "SUCCESS", System.currentTimeMillis() - t0, null, null);
                } else if (r == RenewResult.SKIPPED) {
                    skipped++;
                    batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                            account.getAccountName(), "SKIPPED", System.currentTimeMillis() - t0, "skip: " + r.name(), null);
                } else {
                    failed++;
                    String reason = "renew failed: " + r.name();
                    if (failureSummary.length() > 0) failureSummary.append("; ");
                    failureSummary.append(account.getAccountName()).append(": ").append(reason);
                    batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                            account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, reason, null);
                }
                updateScheduleAfterRun(account.getId(), r);
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append(account.getAccountName()).append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                        account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, reason, null);
                updateScheduleAfterRun(account.getId(), RenewResult.FAILED);
                log.warn("[A1] renew account {} failed: {}", account.getId(), reason);
            }
        }

        // 2. 收尾批次日志 + B9 批次
        endLog(batch, success, failed, skipped, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0;
        boolean failedAll = success == 0 && failed > 0;
        batchJobService.endBatch(job.getId(), partial, failedAll,
                String.format("total=%d success=%d failed=%d skipped=%d", candidates.size(), success, failed, skipped));
        return batch.getId();
    }

    // ==================== 单账号刷新 ====================

    /**
     * 对单个账号执行浏览器刷新：检测失效 → 启动 Chrome 容器 → 提取新 Cookie → 写回。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewResult renewOne(XianyuAccount account) {
        // 熔断器检查：账号已在风控冷却中则跳过
        if (!circuitBreaker.allowRequest(account.getId(), "COOKIE_RENEW")) {
            log.debug("[A1] account {} in circuit-break, skip", account.getId());
            return RenewResult.SKIPPED;
        }

        // 1. 检测当前 Cookie 是否真失效（onlyOnExpired=1 时跳过未失效账号）
        CookieRefreshSchedule schedule = scheduleMapper.selectByAccountId(account.getId());
        boolean onlyOnExpired = schedule == null || schedule.getOnlyOnExpired() == null || schedule.getOnlyOnExpired() == 1;
        if (onlyOnExpired) {
            RenewResult status = checkCookieStatus(account);
            if (status != RenewResult.EXPIRED) {
                // Cookie 仍健康，无需刷新
                return status == RenewResult.SUCCESS ? RenewResult.SKIPPED : status;
            }
        }

        // 2. A2 优先通道：先尝试 MTOP 轻量续期（不启动 Chrome 容器，省资源）
        //    典型场景：_m_h5_tk token 过期但 cookie2/unb 登录态仍健康，A2 调一次 MTOP 即可续 token。
        //    A2 成功 → 直接返回；A2 失败 → 降级到 A1 浏览器刷新。
        try {
            ApiCookieRenewService.RenewResult apiResult = apiCookieRenewService.renewViaApi(account);
            if (apiResult == ApiCookieRenewService.RenewResult.SUCCESS) {
                return RenewResult.SUCCESS;
            }
            if (apiResult == ApiCookieRenewService.RenewResult.SKIPPED) {
                return RenewResult.SKIPPED;
            }
            log.info("[A1] A2 API renew failed for account {}, fall back to browser refresh", account.getId());
        } catch (Exception e) {
            log.warn("[A1] A2 API renew threw, fall back to browser refresh: {}", e.getMessage());
        }

        // 3. A1 降级通道：启动/复用账号独占 Chrome 容器
        if (!accountService.launchChromeContainer(account)) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "Chrome 容器启动失败");
            return RenewResult.FAILED;
        }

        // 4. 通过 CDP Network.getCookies 提取新 Cookie（导航到 goofish.com 后）
        Optional<String> newCookieOpt = extractCookieViaCdp(account);
        if (newCookieOpt.isEmpty()) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "CDP 提取 Cookie 失败");
            return RenewResult.FAILED;
        }
        String newCookie = newCookieOpt.get();

        // 5. 校验新 Cookie 有效性
        XianyuLoginApiService.LoginStatusResult verify = new XianyuLoginApiService(newCookie).checkLoginStatus(newCookie);
        if (verify == null || !verify.loggedIn) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "新 Cookie 校验未通过");
            // 仅「真正登录态失效」才推送 ACCOUNT_COOKIE_EXPIRED；
            // 可恢复签名错误（TOKEN_EXOIRED / ILLEGAL_REQUEST / TOKEN_EMPTY 等）只是 _m_h5_tk 抖动，
            // 登录 cookie 本体仍健康，误推送会让用户以为 cookie 过期。
            if (isRealLoginExpired(verify)) {
                publishLoginExpired(account);
            }
            return RenewResult.FAILED;
        }

        // 6. 写回加密 Cookie + 恢复 ACTIVE
        account.setCookieHeader(newCookie);
        account.setStatus("ACTIVE");
        account.setLastError(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        circuitBreaker.recordSuccess(account.getId(), "COOKIE_RENEW");
        return RenewResult.SUCCESS;
    }

    /**
     * 检测账号当前 Cookie 状态：SUCCESS（健康）/ EXPIRED（失效）/ FAILED（连接异常或可恢复错误）。
     *
     * <p>严格区分「真正登录态失效」与「可恢复签名错误」：</p>
     * <ul>
     *   <li>TOKEN_EXOIRED / ILLEGAL_REQUEST / TOKEN_EMPTY 是 _m_h5_tk 签名 token 过期或预热抖动，
     *       登录态 cookie 本体仍健康（闲鱼登录 cookie 可存活几十天），不应判为 EXPIRED 触发整号刷新。
     *       返回 FAILED 让上层按「连接异常」处理，等待下一轮重试。</li>
     *   <li>仅明确的登录失效（login-error / user-validate / session-expired / fail_sys_login）才判 EXPIRED。</li>
     * </ul>
     */
    private RenewResult checkCookieStatus(XianyuAccount account) {
        String cookie = account.getCookieHeader();
        if (cookie == null || cookie.isBlank()) return RenewResult.EXPIRED;
        try {
            XianyuLoginApiService.LoginStatusResult r = new XianyuLoginApiService(cookie).checkLoginStatus(cookie);
            if (r != null && r.loggedIn) return RenewResult.SUCCESS;
            String msg = r == null ? "" : (r.message == null ? "" : r.message.toLowerCase());
            // 可恢复签名错误：不判 EXPIRED，避免误触发整号 cookie 刷新
            if (msg.contains("token_exoired")
                    || msg.contains("illegal_request")
                    || msg.contains("token_empty")
                    || msg.contains("recoverable sign error")) {
                return RenewResult.FAILED;
            }
            // 真正登录态失效：login-error / user-validate / session-expired / fail_sys_login 等
            if (msg.contains("login") || msg.contains("user_validate") || msg.contains("session_expired")) {
                return RenewResult.EXPIRED;
            }
            // 其余 FAIL_SYS_xxx 系统错误：连接异常/限流等，不算 cookie 失效
            return RenewResult.FAILED;
        } catch (Exception e) {
            return RenewResult.FAILED;
        }
    }

    /**
     * 通过 CDP 从账号独占 Chrome 容器提取 goofish/taobao 生态 Cookie。
     *
     * <p>链路：{@code ChromeProfileManager.getCdpEndpoint(accountId)} 拿到 CDP HTTP 端点
     * （如 {@code http://127.0.0.1:9222}）→ 请求 {@code /json/version} 拿到浏览器级
     * {@code webSocketDebuggerUrl} → 经 {@link CdpSession} 建 WebSocket →
     * {@link CdpCookieStore#getAllCookies()}（即 {@code Network.getAllCookies}）读取整个 cookie jar →
     * 过滤 goofish/taobao 生态域并去重 → 拼成 {@code k1=v1; k2=v2} header 返回。</p>
     *
     * <p>与 {@code XianyuCaptchaSolver.extractCookie} 同思路，但复用更现代的
     * {@link CdpSession}/{@link CdpCookieStore} 客户端栈（自动 id 匹配、事件订阅、超时保护）。
     * 容器已由 {@code renewOne} 启动且 profile 目录持久化登录态，无需再导航到 goofish 页。</p>
     *
     * <p>任何一步失败都返回 {@link Optional#empty()}，让上层链路降级到 A3 重新登录，不抛异常。</p>
     */
    private Optional<String> extractCookieViaCdp(XianyuAccount account) {
        String endpoint = resolveCdpEndpoint(account);
        if (endpoint == null || endpoint.isBlank()) {
            log.warn("[A1] extractCookieViaCdp: 无可用 CDP 端点, account {}", account.getId());
            return Optional.empty();
        }
        String wsUrl;
        try {
            wsUrl = resolveBrowserWsUrl(endpoint);
        } catch (Exception e) {
            log.warn("[A1] extractCookieViaCdp: 解析 CDP WS 端点失败, account {}, err={}",
                    account.getId(), e.getMessage());
            return Optional.empty();
        }
        if (wsUrl == null || wsUrl.isBlank()) {
            log.warn("[A1] extractCookieViaCdp: 未获取到浏览器级 WS 端点, account {}", account.getId());
            return Optional.empty();
        }

        try (CdpSession session = CdpSession.connect(wsUrl, CDP_HTTP)) {
            // 先 enable Network 域（与 XianyuCaptchaSolver 一致，避免部分 Chrome 版本拒绝 cookie 命令）
            session.send("Network.enable", null);
            CdpCookieStore store = new CdpCookieStore(session);
            List<CdpCookieStore.Cookie> all = store.getAllCookies();
            // 过滤生态域 + 按 name 去重（保留最后读到的值，避免同 name 多域副本污染 MTOP 请求）
            LinkedHashMap<String, CdpCookieStore.Cookie> dedup = new LinkedHashMap<>();
            for (CdpCookieStore.Cookie c : all) {
                if (c == null || c.name == null || c.name.isEmpty() || c.domain == null) continue;
                if (!isRelatedDomain(c.domain)) continue;
                dedup.put(c.name, c);
            }
            if (dedup.isEmpty()) {
                log.warn("[A1] extractCookieViaCdp: 容器 cookie jar 中无 goofish/taobao 生态 cookie, account {}",
                        account.getId());
                return Optional.empty();
            }
            String header = CdpCookieStore.toHeaderValue(new ArrayList<>(dedup.values()));
            if (header.isBlank()) {
                return Optional.empty();
            }
            log.info("[A1] extractCookieViaCdp: 提取到 {} 个生态 cookie, account {}",
                    dedup.size(), account.getId());
            return Optional.of(header);
        } catch (Exception e) {
            log.warn("[A1] extractCookieViaCdp: CDP 提取异常, account {}, err={}",
                    account.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析账号 Chrome 容器的 CDP HTTP 端点：优先 {@code ChromeProfileManager.getCdpEndpoint}，
     * 失败则回落到账号 {@code cdpPort} 字段拼 {@code http://127.0.0.1:<port>}。
     */
    private String resolveCdpEndpoint(XianyuAccount account) {
        try {
            var cpm = accountService.getChromeProfileManager();
            if (cpm != null) {
                var ep = cpm.getCdpEndpoint(account.getId());
                if (ep.isPresent() && !ep.get().isBlank()) {
                    return normalizeEndpoint(ep.get());
                }
            }
        } catch (Exception ignored) {
            // 容器未就绪或 ChromeProfileManager 不可用，回落到 cdpPort
        }
        Integer port = account.getCdpPort();
        if (port != null && port > 0) {
            return "http://127.0.0.1:" + port;
        }
        return null;
    }

    /** 去掉尾部斜杠，统一成 {@code http://host:port} 形式。 */
    private static String normalizeEndpoint(String endpoint) {
        String s = endpoint.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * 请求 {@code /json/version} 拿到浏览器级 {@code webSocketDebuggerUrl}。
     * 浏览器级会话支持 {@code Network.getAllCookies}（跨所有 target 读取整个 cookie jar）。
     */
    private String resolveBrowserWsUrl(String endpoint) throws Exception {
        Request req = new Request.Builder()
                .url(endpoint + "/json/version")
                .get()
                .build();
        try (Response resp = CDP_HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("CDP /json/version HTTP " + resp.code());
            }
            try (ResponseBody body = resp.body()) {
                if (body == null) throw new IllegalStateException("CDP /json/version 响应体为空");
                JsonNode json = JSON.readTree(body.string());
                String ws = json.path("webSocketDebuggerUrl").asText("");
                if (ws.isBlank()) throw new IllegalStateException("CDP /json/version 无 webSocketDebuggerUrl");
                return ws;
            }
        }
    }

    /** 判断域名是否属于 goofish/taobao 生态（含子域）。 */
    private static boolean isRelatedDomain(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        for (String related : RELATED_DOMAINS) {
            if (d.equals(related) || d.endsWith("." + related)) return true;
        }
        return false;
    }

    // ==================== 批次日志辅助 ====================

    private List<XianyuAccount> collectCandidates(boolean onlyExpiredOnly) {
        List<CookieRefreshSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CookieRefreshSchedule>().eq(CookieRefreshSchedule::getEnabled, 1));
        return schedules.stream()
                .filter(s -> !onlyExpiredOnly || s.getOnlyOnExpired() == null || s.getOnlyOnExpired() == 1)
                .filter(s -> s.getNextRunAt() == null || !s.getNextRunAt().isAfter(LocalDateTime.now()))
                .map(s -> accountMapper.selectById(s.getAccountId()))
                .filter(a -> a != null && !"DISABLED".equals(a.getStatus()))
                .toList();
    }

    private void updateScheduleAfterRun(Long accountId, RenewResult result) {
        CookieRefreshSchedule schedule = scheduleMapper.selectByAccountId(accountId);
        if (schedule == null) return;
        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setLastResult(result.name());
        schedule.setLastFailureReason(result == RenewResult.FAILED ? "见批次明细" : null);
        // 失败则 30 分钟后重试，成功则按 intervalMinutes 排下次
        int interval = schedule.getIntervalMinutes() != null ? schedule.getIntervalMinutes() : 720;
        schedule.setNextRunAt(LocalDateTime.now().plusMinutes(result == RenewResult.FAILED ? 30 : interval));
        scheduleMapper.updateById(schedule);
    }

    private ScheduledCookiesRefreshLog startLog(String triggerSource, int total) {
        ScheduledCookiesRefreshLog row = new ScheduledCookiesRefreshLog();
        row.setTriggerSource(triggerSource);
        row.setTotalCount(total);
        row.setSuccessCount(0);
        row.setFailedCount(0);
        row.setSkippedCount(0);
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        logMapper.insert(row);
        return row;
    }

    private void endLog(ScheduledCookiesRefreshLog batch, int success, int failed, int skipped, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        String summary = failureSummary == null ? "" : failureSummary;
        if (summary.length() > 2000) summary = summary.substring(0, 2000);
        batch.setFailureSummary(summary);
        logMapper.updateById(batch);
    }

    private void publishLoginExpired(XianyuAccount account) {
        String name = Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName());
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_COOKIE_EXPIRED", account.getId(), name,
                Map.of("accountName", name, "reason", "cookie_renew_failed")));
    }

    /**
     * 判断登录态校验结果是否属于「真正登录态失效」。
     *
     * <p>严格区分两类失败：</p>
     * <ul>
     *   <li><b>可恢复签名错误</b>（TOKEN_EXOIRED / ILLEGAL_REQUEST / TOKEN_EMPTY / recoverable sign error）：
     *       _m_h5_tk 签名 token 过期或预热抖动，登录态 cookie 本体仍健康（闲鱼登录 cookie 可存活几十天），
     *       <b>不算</b> cookie 过期，不应推送 ACCOUNT_COOKIE_EXPIRED。</li>
     *   <li><b>真正登录态失效</b>（login-error / user-validate / session-expired / fail_sys_login）：
     *       登录 cookie 真的失效，需人工重新登录。</li>
     * </ul>
     *
     * <p>实现原则：<b>白名单确认</b>——只有明确的登录失效码才返回 true，
     * 其余（含 null / 空消息 / 系统错误 / 限流 / token 过期等）一律返回 false，
     * 宁可多重试也不误报过期。</p>
     */
    private boolean isRealLoginExpired(XianyuLoginApiService.LoginStatusResult verify) {
        if (verify == null || verify.message == null) return false;
        String msg = verify.message.toLowerCase();
        // 可恢复签名错误：明确排除
        if (msg.contains("token_exoired")
                || msg.contains("illegal_request")
                || msg.contains("token_empty")
                || msg.contains("recoverable sign error")) {
            return false;
        }
        // 真正登录态失效：白名单
        return msg.contains("not logged in")
                || msg.contains("login error")
                || msg.contains("user_validate")
                || msg.contains("session_expired")
                || msg.contains("fail_sys_login");
    }

    /** 单账号刷新结果。 */
    public enum RenewResult {
        SUCCESS,   // 刷新成功
        SKIPPED,   // 跳过（冷却中 / Cookie 仍健康）
        EXPIRED,   // 检测到 Cookie 已失效（需刷新）
        FAILED     // 刷新失败
    }
}
