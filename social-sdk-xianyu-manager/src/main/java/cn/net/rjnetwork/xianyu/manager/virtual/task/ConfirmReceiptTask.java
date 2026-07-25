package cn.net.rjnetwork.xianyu.manager.virtual.task;

import cn.net.rjnetwork.xianyu.manager.virtual.service.ConfirmReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动确认收货定时任务入口 —— A10。
 * <p>由 ScheduledTasks 统一调度（cron 每天 10:00 一次），调 ConfirmReceiptService.runBatch 执行批次。
 * 扫已发货 N 天但买家未确认的订单，按 confirmReceiptMessage 模板发话术催确认。</p>
 */
@Component
public class ConfirmReceiptTask {

    private static final Logger log = LoggerFactory.getLogger(ConfirmReceiptTask.class);

    private final ConfirmReceiptService confirmReceiptService;

    public ConfirmReceiptTask(ConfirmReceiptService confirmReceiptService) {
        this.confirmReceiptService = confirmReceiptService;
    }

    /** 定时执行催确认收货批次。由 ScheduledTasks.runConfirmReceipt 调用。 */
    public void runScheduled() {
        try {
            Long batchId = confirmReceiptService.runBatch("SCHEDULER");
            log.info("[A10] confirm receipt batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[A10] confirm receipt batch failed: {}", e.getMessage());
        }
    }
}
