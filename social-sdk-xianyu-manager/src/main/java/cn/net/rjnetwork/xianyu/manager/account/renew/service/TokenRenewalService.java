package cn.net.rjnetwork.xianyu.manager.account.renew.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuMtopRequestBuilder;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ImTokenCacheMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledTokenRenewalLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ImTokenCache;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledTokenRenewalLog;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Token/IM 续期服务 —— A4。
 * <p>定时扫描 im_token_cache 续期到期 / 失效的账号，调 MTOP pc.login.token 拿新 token；
 * 若被风控 punish 则联动滑块（{@link XianyuCaptchaSolver#solve}）刷新 x5sec，
 * 写回本表 + 账号 imCookieHeader。</p>
 *
 * <p>与 A1/A2/A3 的区别：A1-A3 续「登录 cookie」，A4 续「IM token + x5sec」——
 * 这些是 MTOP 调用与 IM 长连接专用，失效触发风控，需单独走滑块链路刷新。</p>
 *
 * <p>滑块联动复用 captchaSolver.solve（与 MessageService.solveCaptchaAndRetry 同链路），
 * 拿到的 x5sec 写入账号 imCookieHeader + 本表 x5sec 字段。</p>
 */
@Service
public class TokenRenewalService {

    private static final Logger log = LoggerFactory.getLogger(TokenRenewalService.class);
    private static final String JOB_TYPE = "token_renewal";
    private static final String LOGIN_TOKEN_API = "mtop.taobao.idlemessage.pc.login.token";
    private static final String LOGIN_TOKEN_VERSION = "1.0";
    /** token 默认续期间隔（分钟），_m_h5_tk 通常 2 小时过期，提前 10 分钟续 */
    private static final int RENEW_INTERVAL_MINUTES = 110;

    private final AccountMapper accountMapper;
    private final ImTokenCacheMapper tokenCacheMapper;
    private final ScheduledTokenRenewalLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;
    private final XianyuCaptchaSolver captchaSolver;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;

    public TokenRenewalService(AccountMapper accountMapper,
                                ImTokenCacheMapper tokenCacheMapper,
                                ScheduledTokenRenewalLogMapper logMapper,
                                CircuitBreakerService circuitBreaker,
                                BatchJobService batchJobService,
                                XianyuCaptchaSolver captchaSolver) {
        this.accountMapper = accountMapper;
        this.tokenCacheMapper = tokenCacheMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
        this.captchaSolver = captchaSolver;
    }

    // ==================== 批次执行入口 ====================

    /**
     * 执行一次 Token/IM 续期批次 —— 由 TokenRenewalTask 定时调用，也可管理端手动触发。
     * @param triggerSource SCHEDULER / MANUAL / SYSTEM（消息同步触发）
     * @return 批次日志主键
     */
    public Long runBatch(String triggerSource) {
        // 收集待续期账号：nextRenewAt 已到 或 token 已过期
        List<ImTokenCache> candidates = tokenCacheMapper.selectList(
                new LambdaQueryWrapper<ImTokenCache>()
                        .isNull(ImTokenCache::getNextRenewAt)
                        .or()
                        .le(ImTokenCache::getNextRenewAt, LocalDateTime.now()));
        // 过滤掉账号已 DISABLED / COOKIE_EXPIRED 的
        candidates = candidates.stream()
                .filter(c -> {
                    XianyuAccount acc = accountMapper.selectById(c.getAccountId());
                    return acc != null && !"DISABLED".equals(acc.getStatus())
                            && !"COOKIE_EXPIRED".equals(acc.getStatus());
                })
                .toList();

        ScheduledTokenRenewalLog batch = startLog(triggerSource, candidates.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0, captchaTriggered = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (ImTokenCache cache : candidates) {
            long t0 = System.currentTimeMillis();
            XianyuAccount account = accountMapper.selectById(cache.getAccountId());
            try {
                RenewOutcome r = renewOne(account, cache);
                switch (r) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SUCCESS", System.currentTimeMillis() - t0, null, null);
                    }
                    case CAPTCHA_SOLVED -> {
                        success++;
                        captchaTriggered++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SUCCESS", System.currentTimeMillis() - t0,
                                "captcha solved", null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "SKIPPED", System.currentTimeMillis() - t0, "skip", null);
                    }
                    case FAILED -> {
                        failed++;
                        if (failureSummary.length() > 0) failureSummary.append("; ");
                        failureSummary.append(account.getAccountName()).append(": token renew failed");
                        batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                                account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, "renew failed", null);
                    }
                }
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append(account.getAccountName()).append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(account.getId()),
                        account.getAccountName(), "FAILED", System.currentTimeMillis() - t0, reason, null);
            }
        }

        endLog(batch, success, failed, skipped, captchaTriggered, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0;
        boolean failedAll = success == 0 && failed > 0;
        batchJobService.endBatch(job.getId(), partial, failedAll,
                String.format("total=%d success=%d failed=%d skipped=%d captchaTriggered=%d",
                        candidates.size(), success, failed, skipped, captchaTriggered));
        return batch.getId();
    }

    // ==================== 单账号续期 ====================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewOutcome renewOne(XianyuAccount account, ImTokenCache cache) {
        if (!circuitBreaker.allowRequest(account.getId(), "TOKEN_RENEWAL")) {
            return RenewOutcome.SKIPPED;
        }
        try {
            // 1. 构造 MTOP client，调 pc.login.token 拿新 token
            XianyuMtopApiClient client = xianyuMtopClientFactory.create(account);
            if (account.getImCookieHeader() != null && !account.getImCookieHeader().isBlank()) {
                client.setImCookieHeader(account.getImCookieHeader());
            }
            JsonNode resp = client.callMtop(LOGIN_TOKEN_API, LOGIN_TOKEN_VERSION, "{}");
            String newCookie = client.getMergedCookie();

            // 2. 检测风控：若响应含 punish / FAIL_SYS_USER_VALIDATE → 联动滑块
            String rawError = client.getLastErrorResponse();
            if (rawError != null && (rawError.contains("punish") || rawError.contains("FAIL_SYS_USER_VALIDATE")
                    || rawError.contains("x5sec"))) {
                log.info("[A4] account {} token renew hit risk control, invoking captcha solver", account.getId());
                RenewOutcome captchaResult = solveCaptchaAndRefreshX5sec(account, cache, rawError);
                if (captchaResult == RenewOutcome.CAPTCHA_SOLVED) {
                    return captchaResult;
                }
                circuitBreaker.recordFailure(account.getId(), "TOKEN_RENEWAL", "滑块解题失败");
                updateCacheAfterRun(cache, RenewOutcome.FAILED, "滑块解题失败");
                return RenewOutcome.FAILED;
            }

            // 3. 拿到新 token，写回缓存 + imCookieHeader
            if (newCookie == null || newCookie.isBlank()) {
                circuitBreaker.recordFailure(account.getId(), "TOKEN_RENEWAL", "MTOP 响应无新 cookie");
                updateCacheAfterRun(cache, RenewOutcome.FAILED, "MTOP 响应无新 cookie");
                return RenewOutcome.FAILED;
            }
            applyNewToken(cache, newCookie);
            circuitBreaker.recordSuccess(account.getId(), "TOKEN_RENEWAL");
            return RenewOutcome.SUCCESS;
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            circuitBreaker.recordFailure(account.getId(), "TOKEN_RENEWAL", reason);
            updateCacheAfterRun(cache, RenewOutcome.FAILED, reason);
            log.warn("[A4] account {} token renew failed: {}", account.getId(), reason);
            return RenewOutcome.FAILED;
        }
    }

    /** 联动滑块解题刷新 x5sec：抽 punish URL → captchaSolver.solve → 拿新 x5sec 写回。 */
    private RenewOutcome solveCaptchaAndRefreshX5sec(XianyuAccount account, ImTokenCache cache, String rawError) {
        try {
            // 抽 punish URL（与 MessageService.extractPunishUrlFromRaw 同逻辑）
            String punishUrl = extractPunishUrlFromRaw(rawError);
            if (punishUrl == null || punishUrl.isBlank()) {
                log.warn("[A4] account {} no punish URL in raw error, skip captcha", account.getId());
                return RenewOutcome.FAILED;
            }
            // 调滑块解题（复用 XianyuCaptchaSolver，与消息同步链路同链路）
            String accountCdpEndpoint = account.getCdpPort() != null && account.getCdpPort() > 0
                    ? "http://127.0.0.1:" + account.getCdpPort()
                    : null;
            long seed = account.getChromeSeed() != null ? account.getChromeSeed() : account.getId();
            var result = captchaSolver.solve(punishUrl, accountCdpEndpoint, seed,
                    account.getCookieHeader(), account.getImCookieHeader());
            if (result != null && result.isSuccess() && result.getNewCookie() != null) {
                // x5sec 等 IM 专用 cookie 写入账号 imCookieHeader + 本表
                account.setImCookieHeader(result.getNewCookie());
                account.setUpdatedAt(LocalDateTime.now());
                accountMapper.updateById(account);
                cache.setX5sec(extractCookieValue(result.getNewCookie(), "x5sec"));
                cache.setImCookieHeader(result.getNewCookie());
                cache.setLastRenewAt(LocalDateTime.now());
                cache.setLastResult("CAPTCHA_SOLVED");
                cache.setLastFailureReason(null);
                cache.setConsecutiveFailures(0);
                cache.setNextRenewAt(LocalDateTime.now().plusMinutes(RENEW_INTERVAL_MINUTES));
                tokenCacheMapper.updateById(cache);
                return RenewOutcome.CAPTCHA_SOLVED;
            }
            return RenewOutcome.FAILED;
        } catch (Exception e) {
            log.warn("[A4] account {} captcha solve failed: {}", account.getId(), e.getMessage());
            return RenewOutcome.FAILED;
        }
    }

    /** 写回新 token 到缓存 + 账号 imCookieHeader。 */
    private void applyNewToken(ImTokenCache cache, String newCookie) {
        cache.setMtopTokenCookie(XianyuMtopRequestBuilder.getCookieValue(newCookie, "_m_h5_tk"));
        cache.setMtopToken(extractTokenFromCookie(cache.getMtopTokenCookie()));
        cache.setImCookieHeader(newCookie);
        cache.setLastRenewAt(LocalDateTime.now());
        cache.setLastResult("SUCCESS");
        cache.setLastFailureReason(null);
        cache.setConsecutiveFailures(0);
        cache.setNextRenewAt(LocalDateTime.now().plusMinutes(RENEW_INTERVAL_MINUTES));
        tokenCacheMapper.updateById(cache);
    }

    private void updateCacheAfterRun(ImTokenCache cache, RenewOutcome result, String reason) {
        cache.setLastRenewAt(LocalDateTime.now());
        cache.setLastResult(result.name());
        cache.setLastFailureReason(reason);
        if (result == RenewOutcome.FAILED) {
            cache.setConsecutiveFailures(Optional.ofNullable(cache.getConsecutiveFailures()).orElse(0) + 1);
            // 失败 15 分钟后重试
            cache.setNextRenewAt(LocalDateTime.now().plusMinutes(15));
        } else {
            cache.setNextRenewAt(LocalDateTime.now().plusMinutes(RENEW_INTERVAL_MINUTES));
        }
        tokenCacheMapper.updateById(cache);
    }

    // ==================== 辅助 ====================

    /** 从原始错误文本抽 punish URL（与 MessageService.extractPunishUrlFromRaw 同逻辑简化版）。 */
    private String extractPunishUrlFromRaw(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf("http");
        if (idx < 0) return null;
        // 找到第一个 http 开头到下一个空白或字符串尾
        int end = idx;
        while (end < raw.length() && !Character.isWhitespace(raw.charAt(end))) end++;
        return raw.substring(idx, end);
    }

    private String extractCookieValue(String cookieHeader, String name) {
        return XianyuMtopRequestBuilder.getCookieValue(cookieHeader, name);
    }

    private String extractTokenFromCookie(String tokenCookie) {
        if (tokenCookie == null) return null;
        int us = tokenCookie.indexOf('_');
        return us > 0 ? tokenCookie.substring(0, us) : tokenCookie;
    }

    private ScheduledTokenRenewalLog startLog(String triggerSource, int total) {
        ScheduledTokenRenewalLog row = new ScheduledTokenRenewalLog();
        row.setTriggerSource(triggerSource);
        row.setTotalCount(total);
        row.setSuccessCount(0);
        row.setFailedCount(0);
        row.setSkippedCount(0);
        row.setCaptchaTriggeredCount(0);
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.now());
        logMapper.insert(row);
        return row;
    }

    private void endLog(ScheduledTokenRenewalLog batch, int success, int failed, int skipped,
                        int captchaTriggered, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setCaptchaTriggeredCount(captchaTriggered);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        if (failureSummary != null && failureSummary.length() > 2000) failureSummary = failureSummary.substring(0, 2000);
        batch.setFailureSummary(failureSummary);
        logMapper.updateById(batch);
    }

    /** 续期结果。 */
    public enum RenewOutcome {
        SUCCESS,           // token 续期成功
        SKIPPED,           // 跳过（熔断中）
        CAPTCHA_SOLVED,    // 触发滑块并成功刷新 x5sec
        FAILED             // 续期失败
    }
}
