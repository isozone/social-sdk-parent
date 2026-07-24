package cn.net.rjnetwork.xianyu.manager.account.renew.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final AccountMapper accountMapper;
    private final CookieRefreshScheduleMapper scheduleMapper;
    private final ScheduledCookiesRefreshLogMapper logMapper;
    private final AccountService accountService;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;
    private final ApplicationEventPublisher eventPublisher;

    public CookieRenewService(AccountMapper accountMapper,
                              CookieRefreshScheduleMapper scheduleMapper,
                              ScheduledCookiesRefreshLogMapper logMapper,
                              AccountService accountService,
                              CircuitBreakerService circuitBreaker,
                              BatchJobService batchJobService,
                              ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
        this.accountService = accountService;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
        this.eventPublisher = eventPublisher;
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

        // 2. 启动/复用账号独占 Chrome 容器
        if (!accountService.launchChromeContainer(account)) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "Chrome 容器启动失败");
            return RenewResult.FAILED;
        }

        // 3. 通过 CDP Network.getCookies 提取新 Cookie（导航到 goofish.com 后）
        Optional<String> newCookieOpt = extractCookieViaCdp(account);
        if (newCookieOpt.isEmpty()) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "CDP 提取 Cookie 失败");
            return RenewResult.FAILED;
        }
        String newCookie = newCookieOpt.get();

        // 4. 校验新 Cookie 有效性
        XianyuLoginApiService.LoginStatusResult verify = new XianyuLoginApiService(newCookie).checkLoginStatus(newCookie);
        if (verify == null || !verify.loggedIn) {
            circuitBreaker.recordFailure(account.getId(), "COOKIE_RENEW", "新 Cookie 校验未通过");
            // Cookie 彻底失效 → 触发 A3 登录续期通知
            publishLoginExpired(account);
            return RenewResult.FAILED;
        }

        // 5. 写回加密 Cookie + 恢复 ACTIVE
        account.setCookieHeader(newCookie);
        account.setStatus("ACTIVE");
        account.setLastError(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        circuitBreaker.recordSuccess(account.getId(), "COOKIE_RENEW");
        return RenewResult.SUCCESS;
    }

    /** 检测账号当前 Cookie 状态：SUCCESS（健康）/ EXPIRED（失效）/ FAILED（连接异常）。 */
    private RenewResult checkCookieStatus(XianyuAccount account) {
        String cookie = account.getCookieHeader();
        if (cookie == null || cookie.isBlank()) return RenewResult.EXPIRED;
        try {
            XianyuLoginApiService.LoginStatusResult r = new XianyuLoginApiService(cookie).checkLoginStatus(cookie);
            if (r != null && r.loggedIn) return RenewResult.SUCCESS;
            String msg = r == null ? "" : (r.message == null ? "" : r.message.toLowerCase());
            if (msg.contains("login") || msg.contains("token") || msg.contains("empty")) return RenewResult.EXPIRED;
            return RenewResult.FAILED;
        } catch (Exception e) {
            return RenewResult.FAILED;
        }
    }

    /**
     * 通过 CDP Network.getCookies 从账号独占 Chrome 容器提取 goofish 域 Cookie。
     * 容器已在 renewOne 中启动，这里直接走 CDP HTTP。
     */
    private Optional<String> extractCookieViaCdp(XianyuAccount account) {
        // TODO: 调 ChromeProfileManager.getCdpEndpoint(accountId) 拿端点，
        //       再走 CDP HTTP /json + WebSocket Network.getCookies 提取 goofish.com 域所有 cookie，
        //       拼成 cookie header 形式（k1=v1; k2=v2）返回。
        //       链路与 XianyuCaptchaSolver.extractCookie 类似，可后续抽公共 CDP cookie 工具。
        // 当前先返回空让链路降级到 A3，待 ChromeProfileManager 暴露 CDP client 后补全。
        log.warn("[A1] extractCookieViaCdp not yet wired for account {}, treat as failed", account.getId());
        return Optional.empty();
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
        if (failureSummary.length() > 2000) failureSummary.setLength(2000);
        batch.setFailureSummary(failureSummary.toString());
        logMapper.updateById(batch);
    }

    private void publishLoginExpired(XianyuAccount account) {
        String name = Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName());
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_COOKIE_EXPIRED", account.getId(), name,
                Map.of("accountName", name, "reason", "cookie_renew_failed")));
    }

    /** 单账号刷新结果。 */
    public enum RenewResult {
        SUCCESS,   // 刷新成功
        SKIPPED,   // 跳过（冷却中 / Cookie 仍健康）
        EXPIRED,   // 检测到 Cookie 已失效（需刷新）
        FAILED     // 刷新失败
    }
}
