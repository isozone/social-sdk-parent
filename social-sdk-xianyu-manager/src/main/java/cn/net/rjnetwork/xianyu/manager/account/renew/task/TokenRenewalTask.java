package cn.net.rjnetwork.xianyu.manager.account.renew.task;

import cn.net.rjnetwork.xianyu.manager.account.renew.service.TokenRenewalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token/IM 续期定时任务入口 —— A4。
 * <p>由 ScheduledTasks 统一调度（cron 每 20 分钟一次），调 TokenRenewalService.runBatch 执行批次。
 * 续期 _m_h5_tk token + x5sec，被风控拦截时联动滑块解题刷新。</p>
 */
@Component
public class TokenRenewalTask {

    private static final Logger log = LoggerFactory.getLogger(TokenRenewalTask.class);

    private final TokenRenewalService tokenRenewalService;

    public TokenRenewalTask(TokenRenewalService tokenRenewalService) {
        this.tokenRenewalService = tokenRenewalService;
    }

    /** 定时执行 Token/IM 续期批次。由 ScheduledTasks.runTokenRenewal 调用。 */
    public void runScheduled() {
        try {
            Long batchId = tokenRenewalService.runBatch("SCHEDULER");
            log.info("[A4] token renewal batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[A4] token renewal batch failed: {}", e.getMessage());
        }
    }
}
