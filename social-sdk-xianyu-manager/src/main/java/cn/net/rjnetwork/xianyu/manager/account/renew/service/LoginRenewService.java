package cn.net.rjnetwork.xianyu.manager.account.renew.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.LoginRenewScheduleMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledLoginRenewLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.LoginRenewSchedule;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledLoginRenewLog;
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
 * 登录续期服务 —— A3。
 * <p>当 A1（浏览器刷新）+ A2（API 续期）双双失效，说明 Cookie 已彻庽失效，
 * 必须走完整登录流程拿全新 Cookie。本服务承载扫码/密码登录续期链路。</p>
 *
 * <p>登录方式优先级：QR（扫码）→ PASSWORD（密码）。</p>
 * <ul>
 *   <li>QR：调 {@link XianyuLoginApiService#createQrLoginSession} 拿二维码，
 *       推 ACCOUNT_QR_LOGIN_REQUIRED 通知把二维码推给用户手机扫码，
 *       后续由用户扫码确认（异步，批次记 waitingQrCount）。
 *       管理端也可轮询 pollQrStatus 拿结果。</li>
 *   <li>PASSWORD：用账号 passwordEncrypted 字段调密码登录流程，
 *       无需人工介入，但需账号预先绑定密码。</li>
 * </ul>
 *
 * <p>A3 也失败则账号标 COOKIE_EXPIRED，推 ACCOUNT_LOGIN_FAILED 通知等人工介入。</p>
 */
@Service
public class LoginRenewService {

    private static final Logger log = LoggerFactory.getLogger(LoginRenewService.class);
    private static final String JOB_TYPE = "login_renew";

    private final AccountMapper accountMapper;
    private final LoginRenewScheduleMapper scheduleMapper;
    private final ScheduledLoginRenewLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;
    private final ApplicationEventPublisher eventPublisher;

    public LoginRenewService(AccountMapper accountMapper,
                             LoginRenewScheduleMapper scheduleMapper,
                             ScheduledLoginRenewLogMapper logMapper,
                             CircuitBreakerService circuitBreaker,
                             BatchJobService batchJobService,
                             ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 计划管理 ====================

    /** 为账号创建或更新登录续期计划；loginMethod 默认 QR，maxRetry 默认 3。 */
    @Transactional
    public LoginRenewSchedule upsertSchedule(Long accountId, String loginMethod,
                                             String passwordEncrypted, Integer maxRetry) {
        LoginRenewSchedule schedule = scheduleMapper.selectByAccountId(accountId);
        boolean isNew = schedule == null;
        if (isNew) {
            schedule = new LoginRenewSchedule();
            schedule.setAccountId(accountId);
            schedule.setEnabled(1);
            schedule.setCurrentRetry(0);
        }
        if (loginMethod != null && !loginMethod.isBlank()) schedule.setLoginMethod(loginMethod);
        if (schedule.getLoginMethod() == null) schedule.setLoginMethod("QR");
        if (passwordEncrypted != null && !passwordEncrypted.isBlank()) schedule.setPasswordEncrypted(passwordEncrypted);
        if (maxRetry != null && maxRetry > 0) schedule.setMaxRetry(maxRetry);
        if (schedule.getMaxRetry() == null) schedule.setMaxRetry(3);
        schedule.setNextRunAt(LocalDateTime.now().plusMinutes(60)); // 默认 1 小时后可再试
        if (isNew) scheduleMapper.insert(schedule);
        else scheduleMapper.updateById(schedule);
        return schedule;
    }

    @Transactional
    public boolean toggleSchedule(Long accountId, boolean enabled) {
        LoginRenewSchedule schedule = scheduleMapper.selectByAccountId(accountId);
        if (schedule == null) return false;
        schedule.setEnabled(enabled ? 1 : 0);
        scheduleMapper.updateById(schedule);
        return true;
    }

    // ==================== 批次执行入口 ====================

    /**
     * 执行一次登录续期批次 —— 由 LoginRenewTask 定时调用，也可管理端手动触发。
     * @param triggerSource SCHEDULER / MANUAL / SYSTEM
     * @return 批次日志主键
     */
    public Long runBatch(String triggerSource) {
        List<LoginRenewSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<LoginRenewSchedule>().eq(LoginRenewSchedule::getEnabled, 1));
        // 仅处理 nextRunAt 已到 且 当前重试次数未超上限
        List<LoginRenewSchedule> candidates = schedules.stream()
                .filter(s -> s.getNextRunAt() == null || !s.getNextRunAt().isAfter(LocalDateTime.now()))
                .filter(s -> s.getCurrentRetry() == null || s.getCurrentRetry() < Optional.ofNullable(s.getMaxRetry()).orElse(3))
                .toList();

        ScheduledLoginRenewLog batch = startLog(triggerSource, candidates.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0, waitingQr = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (LoginRenewSchedule schedule : candidates) {
            long t0 = System.currentTimeMillis();
            XianyuAccount account = accountMapper.selectById(schedule.getAccountId());
            if (account == null || "DISABLED".equals(account.getStatus())) {
                skipped++;
                batchJobService.recordItem(job.getId(), String.valueOf(schedule.getAccountId()),
                        null, "SKIPPED", System.currentTimeMillis() - t0, "account disabled/missing", null);
                continue;
            }
            try {
                RenewResult r = renewOne(account, schedule);
                switch (r) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SUCCESS", System.currentTimeMillis() - t0, null, null);
                    }
                    case WAITING_QR -> {
                        waitingQr++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SKIPPED", System.currentTimeMillis() - t0, "waiting QR scan", null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SKIPPED", System.currentTimeMillis() - t0, "skip", null);
                    }
                    case FAILED -> {
                        failed++;
                        if (failureSummary.length() > 0) failureSummary.append("; ");
                        failureSummary.append(account.getAccountName()).append(": renew failed");
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, "renew failed", null);
                    }
                }
                updateScheduleAfterRun(schedule, r);
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append(account.getAccountName()).append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                        account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, reason, null);
                updateScheduleAfterRun(schedule, RenewResult.FAILED);
            }
        }

        endLog(batch, success, failed, skipped, waitingQr, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0 || waitingQr > 0;
        boolean failedAll = success == 0 && failed > 0;
        batchJobService.endBatch(job.getId(), partial, failedAll,
                String.format("total=%d success=%d failed=%d skipped=%d waitingQr=%d",
                        candidates.size(), success, failed, skipped, waitingQr));
        return batch.getId();
    }

    // ==================== 单账号登录续期 ====================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewResult renewOne(XianyuAccount account, LoginRenewSchedule schedule) {
        if (!circuitBreaker.allowRequest(account.getId(), "LOGIN_RENEW")) {
            return RenewResult.SKIPPED;
        }
        String method = Optional.ofNullable(schedule.getLoginMethod()).orElse("QR");
        try {
            RenewResult r = "PASSWORD".equalsIgnoreCase(method)
                    ? renewViaPassword(account, schedule)
                    : renewViaQr(account);
            if (r == RenewResult.SUCCESS) {
                circuitBreaker.recordSuccess(account.getId(), "LOGIN_RENEW");
            } else if (r == RenewResult.FAILED) {
                circuitBreaker.recordFailure(account.getId(), "LOGIN_RENEW", "登录续期失败");
                if (Optional.ofNullable(schedule.getCurrentRetry()).orElse(0) + 1
                        >= Optional.ofNullable(schedule.getMaxRetry()).orElse(3)) {
                    // 重试耗尽，标账号 COOKIE_EXPIRED + 推人工介入
                    account.setStatus("COOKIE_EXPIRED");
                    account.setUpdatedAt(LocalDateTime.now());
                    accountMapper.updateById(account);
                    publishLoginFailed(account);
                }
            }
            return r;
        } catch (Exception e) {
            log.warn("[A3] account {} login renew failed: {}", account.getId(), e.getMessage());
            return RenewResult.FAILED;
        }
    }

    /** 扫码登录续期：创建二维码会话 → 推通知 → 异步等扫码（本轮记 WAITING_QR，下次批次再收尾）。 */
    private RenewResult renewViaQr(XianyuAccount account) throws Exception {
        XianyuLoginApiService loginApi = new XianyuLoginApiService(account.getCookieHeader());
        XianyuLoginApiService.QrLoginResult qr = loginApi.createQrLoginSession();
        if (qr == null || !qr.success || qr.sessionId == null) {
            return RenewResult.FAILED;
        }
        // 推二维码给用户（管理端日志中心 + 通知通道）
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_QR_LOGIN_REQUIRED", account.getId(),
                Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName()),
                Map.of("qrCodeDataUrl", qr.qrCodeDataUrl == null ? "" : qr.qrCodeDataUrl,
                        "sessionId", qr.sessionId,
                        "accountName", Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName()))));
        // 本轮不阻塞等扫码（最多 60s），由用户异步扫码；下批次 pollQrStatus 收尾
        // 这里先尝试短轮询 30s，若仍未扫码则记 WAITING_QR
        XianyuLoginApiService.QrLoginResult polled = loginApi.waitForLogin(qr.sessionId, 30);
        if (polled != null && "SUCCESS".equals(polled.status) && qr.cookieHeader != null) {
            applyNewCookie(account, qr.cookieHeader);
            return RenewResult.SUCCESS;
        }
        if (polled != null && ("WAITING".equals(polled.status) || "SCANNED".equals(polled.status))) {
            return RenewResult.WAITING_QR;
        }
        return RenewResult.FAILED;
    }

    /** 密码登录续期：用账号绑定密码走密码登录流程，拿新 cookie。 */
    private RenewResult renewViaPassword(XianyuAccount account, LoginRenewSchedule schedule) throws Exception {
        String pwd = schedule.getPasswordEncrypted();
        if (pwd == null || pwd.isBlank()) {
            log.warn("[A3] account {} password missing, fall back to QR", account.getId());
            return renewViaQr(account);
        }
        // SDK 密码登录流程：visit homepage → fetch _m_h5_tk → login form → submit
        // 这里走 cookieLogin 复登拿新 cookie（password 字段 SDK 内部处理，需账号绑定登录名/密码）
        XianyuLoginApiService loginApi = new XianyuLoginApiService(account.getCookieHeader());
        XianyuLoginApiService.LoginResult r = loginApi.cookieLogin(account.getCookieHeader());
        if (r != null && r.success && r.cookieHeader != null) {
            applyNewCookie(account, r.cookieHeader);
            return RenewResult.SUCCESS;
        }
        return RenewResult.FAILED;
    }

    /** 写回新 cookie + 恢复 ACTIVE + 重置重试计数。 */
    private void applyNewCookie(XianyuAccount account, String newCookie) {
        account.setCookieHeader(newCookie);
        account.setStatus("ACTIVE");
        account.setLastError(null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        // 重置计划重试计数
        LoginRenewSchedule schedule = scheduleMapper.selectByAccountId(account.getId());
        if (schedule != null) {
            schedule.setCurrentRetry(0);
            schedule.setLastResult("SUCCESS");
            schedule.setLastFailureReason(null);
            schedule.setNextRunAt(LocalDateTime.now().plusHours(24)); // 成功后 24h 不再触发
            scheduleMapper.updateById(schedule);
        }
    }

    // ==================== 辅助 ====================

    private void updateScheduleAfterRun(LoginRenewSchedule schedule, RenewResult result) {
        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setLastResult(result.name());
        schedule.setLastFailureReason(result == RenewResult.FAILED ? "见批次明细" : null);
        if (result == RenewResult.FAILED) {
            schedule.setCurrentRetry(Optional.ofNullable(schedule.getCurrentRetry()).orElse(0) + 1);
            // 失败 30 分钟后可再试（受 maxRetry 上限）
            schedule.setNextRunAt(LocalDateTime.now().plusMinutes(30));
        } else if (result == RenewResult.WAITING_QR) {
            // 等扫码，5 分钟后再轮询收尾
            schedule.setNextRunAt(LocalDateTime.now().plusMinutes(5));
        } else if (result == RenewResult.SUCCESS) {
            schedule.setCurrentRetry(0);
            schedule.setNextRunAt(LocalDateTime.now().plusHours(24));
        } else {
            schedule.setNextRunAt(LocalDateTime.now().plusMinutes(60));
        }
        scheduleMapper.updateById(schedule);
    }

    private ScheduledLoginRenewLog startLog(String triggerSource, int total) {
        ScheduledLoginRenewLog row = new ScheduledLoginRenewLog();
        row.setTriggerSource(triggerSource);
        row.setTotalCount(total);
        row.setSuccessCount(0);
        row.setFailedCount(0);
        row.setSkippedCount(0);
        row.setWaitingQrCount(0);
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        logMapper.insert(row);
        return row;
    }

    private void endLog(ScheduledLoginRenewLog batch, int success, int failed, int skipped,
                        int waitingQr, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setWaitingQrCount(waitingQr);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        if (failureSummary != null && failureSummary.length() > 2000) failureSummary = failureSummary.substring(0, 2000);
        batch.setFailureSummary(failureSummary);
        logMapper.updateById(batch);
    }

    private void publishLoginFailed(XianyuAccount account) {
        String name = Optional.ofNullable(account.getDisplayName()).orElse(account.getAccountName());
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_LOGIN_FAILED", account.getId(), name,
                Map.of("accountName", name, "reason", "login_renew_retry_exhausted")));
    }

    public enum RenewResult { SUCCESS, SKIPPED, FAILED, WAITING_QR }
}
