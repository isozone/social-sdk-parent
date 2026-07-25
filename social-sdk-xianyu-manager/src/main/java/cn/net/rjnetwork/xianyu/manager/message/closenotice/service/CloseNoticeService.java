package cn.net.rjnetwork.xianyu.manager.message.closenotice.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.mapper.ScheduledCloseNoticeLogMapper;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.model.ScheduledCloseNoticeLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 定时关闭平台通知服务 —— B5。
 * <p>定时扫账号未关闭的平台通知，调 {@link XianyuApiFacade#closeNotice} 逐个关闭。
 * 对标参考项目 close_notice_service.py。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫启用账号（status!=DISABLED），熔断器检查（账号风控冷却中则跳过）；</li>
 *   <li>调 facade.getSessionList 拿未关闭通知列表（闲鱼平台通知走 session 列表）；</li>
 *   <li>逐个调 facade.closeNotice(noticeId) 关闭；</li>
 *   <li>写批次日志（scheduled_close_notice_log）+ 明细（B9 batch_job_item, job_type=close_notice）+ 熔断记 success/failure。</li>
 * </ol>
 */
@Service
public class CloseNoticeService {

    private static final Logger log = LoggerFactory.getLogger(CloseNoticeService.class);
    private static final String JOB_TYPE = "close_notice";
    private static final String SCENE = "CLOSE_NOTICE";

    private final AccountMapper accountMapper;
    private final ScheduledCloseNoticeLogMapper logMapper;
    private final CircuitBreakerService circuitBreaker;
    private final BatchJobService batchJobService;

    public CloseNoticeService(AccountMapper accountMapper,
                              ScheduledCloseNoticeLogMapper logMapper,
                              CircuitBreakerService circuitBreaker,
                              BatchJobService batchJobService) {
        this.accountMapper = accountMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.batchJobService = batchJobService;
    }

    /** 执行一次关闭通知批次（多账号）。 */
    public Long runBatch(String triggerSource) {
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<XianyuAccount>()
                .ne(XianyuAccount::getStatus, "DISABLED"));
        ScheduledCloseNoticeLog batch = startLog(triggerSource, accounts.size());
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, accounts.size());
        batch.setBatchJobId(job.getId());
        logMapper.updateById(batch);

        int success = 0, failed = 0, skipped = 0;
        StringBuilder failureSummary = new StringBuilder();
        for (XianyuAccount account : accounts) {
            long t0 = System.currentTimeMillis();
            Long accountId = account.getId();
            try {
                CloseResult r = closeForAccount(account);
                switch (r.state) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(accountId),
                                Optional.ofNullable(account.getAccountName()).orElse("account#" + accountId),
                                "SUCCESS", System.currentTimeMillis() - t0,
                                "closed=" + r.closedCount, null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(accountId),
                                Optional.ofNullable(account.getAccountName()).orElse("account#" + accountId),
                                "SKIPPED", System.currentTimeMillis() - t0, "skip: " + r.reason, null);
                    }
                    case FAILED -> {
                        failed++;
                        if (failureSummary.length() > 0) failureSummary.append("; ");
                        failureSummary.append("account#").append(accountId).append(": ").append(r.reason);
                        batchJobService.recordItem(job.getId(), String.valueOf(accountId),
                                Optional.ofNullable(account.getAccountName()).orElse("account#" + accountId),
                                "FAILED", System.currentTimeMillis() - t0, r.reason, null);
                    }
                }
            } catch (Exception e) {
                failed++;
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (failureSummary.length() > 0) failureSummary.append("; ");
                failureSummary.append("account#").append(accountId).append(": ").append(reason);
                batchJobService.recordItem(job.getId(), String.valueOf(accountId),
                        Optional.ofNullable(account.getAccountName()).orElse("account#" + accountId),
                        "FAILED", System.currentTimeMillis() - t0, reason, null);
            }
        }

        endLog(batch, success, failed, skipped, failureSummary.toString());
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", accounts.size(), success, failed, skipped));
        return batch.getId();
    }

    /** 闲鱼 PC 端平台通知分类 id（mtop.taobao.idlemessage.pc.profile.notice.update 的 noticeId 取值）。
     *  闲鱼侧无「拉通知列表」API，按已知分类逐个关闭，覆盖常见通知场景。 */
    private static final List<String> NOTICE_CATEGORIES = List.of(
            "system",       // 系统通知
            "trade",        // 交易通知
            "interaction",  // 互动通知
            "marketing",    // 营销通知
            "order",        // 订单通知
            "logistics"     // 物流通知
    );

    /** 对单账号关闭所有未关闭通知。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CloseResult closeForAccount(XianyuAccount account) {
        Long accountId = account.getId();
        if (!circuitBreaker.allowRequest(accountId, SCENE)) {
            return new CloseResult(CloseState.SKIPPED, 0, "circuit open");
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            return new CloseResult(CloseState.SKIPPED, 0, "no cookie");
        }
        try {
            XianyuApiFacade facade = new XianyuApiFacade(account.getCookieHeader());
            int closedCount = 0;
            List<String> failedIds = new ArrayList<>();
            // 逐个通知分类调 closeNotice 关闭
            for (String noticeId : NOTICE_CATEGORIES) {
                try {
                    JsonNode closeResp = facade.closeNotice(noticeId);
                    String closeRet = closeResp != null ? closeResp.path("ret").toString() : "";
                    // 闲鱼侧已关闭的分类会返回「重复操作」类提示，不算失败
                    if (closeRet.contains("FAIL") && !closeRet.contains("EXIST") && !closeRet.contains("DUPLICATE")) {
                        failedIds.add(noticeId);
                    } else {
                        closedCount++;
                    }
                } catch (Exception ce) {
                    failedIds.add(noticeId);
                }
            }
            if (closedCount > 0) {
                circuitBreaker.recordSuccess(accountId, SCENE);
            }
            if (!failedIds.isEmpty()) {
                String failedSummary = String.join(",", failedIds);
                return new CloseResult(CloseState.PARTIAL, closedCount,
                        "failed categories=" + failedSummary.substring(0, Math.min(200, failedSummary.length())));
            }
            return new CloseResult(CloseState.SUCCESS, closedCount, null);
        } catch (Exception e) {
            circuitBreaker.recordFailure(accountId, SCENE,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[B5] account {} close notice failed: {}", accountId, e.getMessage());
            return new CloseResult(CloseState.FAILED, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ScheduledCloseNoticeLog startLog(String triggerSource, int total) {
        ScheduledCloseNoticeLog row = new ScheduledCloseNoticeLog();
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

    private void endLog(ScheduledCloseNoticeLog batch, int success, int failed, int skipped, String failureSummary) {
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setStatus(failed > 0 ? (success > 0 ? "PARTIAL" : "FAILED") : "SUCCESS");
        batch.setEndedAt(LocalDateTime.now());
        if (failureSummary != null && failureSummary.length() > 2000) failureSummary = failureSummary.substring(0, 2000);
        batch.setFailureSummary(failureSummary);
        logMapper.updateById(batch);
    }

    private enum CloseState { SUCCESS, SKIPPED, FAILED, PARTIAL }

    private static class CloseResult {
        final CloseState state;
        final int closedCount;
        final String reason;
        CloseResult(CloseState state, int closedCount, String reason) {
            this.state = state; this.closedCount = closedCount; this.reason = reason;
        }
    }
}
