package cn.net.rjnetwork.xianyu.manager.order.rate.task;

import cn.net.rjnetwork.xianyu.manager.order.rate.service.AutoRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动评价定时任务入口 —— B2。
 * <p>由 ScheduledTasks 统一调度（cron 每天 11:00 一次），调 AutoRateService.runBatch 执行批次。
 * 扫已收货 N 天且卖家未评的订单，按 AutoRateConfig 调 reviewOrder 评好评。</p>
 */
@Component
public class AutoRateTask {

    private static final Logger log = LoggerFactory.getLogger(AutoRateTask.class);

    private final AutoRateService autoRateService;

    public AutoRateTask(AutoRateService autoRateService) {
        this.autoRateService = autoRateService;
    }

    /** 定时执行自动评价批次。由 ScheduledTasks.runAutoRate 调用。 */
    public void runScheduled() {
        try {
            Long batchId = autoRateService.runBatch("SCHEDULER");
            log.info("[B2] auto rate batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[B2] auto rate batch failed: {}", e.getMessage());
        }
    }
}
