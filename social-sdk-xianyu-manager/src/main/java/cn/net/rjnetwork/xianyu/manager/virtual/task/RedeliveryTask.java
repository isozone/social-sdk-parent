package cn.net.rjnetwork.xianyu.manager.virtual.task;

import cn.net.rjnetwork.xianyu.manager.virtual.service.RedeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动补发货定时任务入口 —— A9。
 * <p>由 ScheduledTasks 统一调度（cron 每 5 分钟一次），调 RedeliveryService.runBatch 执行批次。
 * 扫 FAILED/SKIPPED 但未超重试上限的 task，按指数退避重跑 A8 主链路。</p>
 */
@Component
public class RedeliveryTask {

    private static final Logger log = LoggerFactory.getLogger(RedeliveryTask.class);

    private final RedeliveryService redeliveryService;

    public RedeliveryTask(RedeliveryService redeliveryService) {
        this.redeliveryService = redeliveryService;
    }

    /** 定时执行补发批次。由 ScheduledTasks.runRedelivery 调用。 */
    public void runScheduled() {
        try {
            Long batchId = redeliveryService.runBatch("SCHEDULER");
            log.info("[A9] redelivery batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[A9] redelivery batch failed: {}", e.getMessage());
        }
    }
}
