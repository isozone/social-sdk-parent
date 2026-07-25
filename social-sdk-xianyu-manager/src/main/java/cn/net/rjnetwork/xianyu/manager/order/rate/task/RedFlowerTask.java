package cn.net.rjnetwork.xianyu.manager.order.rate.task;

import cn.net.rjnetwork.xianyu.manager.order.rate.service.RedFlowerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 求小红花定时任务入口 —— B3。
 * <p>由 ScheduledTasks 统一调度（cron 每天 09:00 一次），调 RedFlowerService.runBatch 执行批次。
 * 给已成交订单的买家送红花提信誉，每日送花上限防风控盯上。</p>
 */
@Component
public class RedFlowerTask {

    private static final Logger log = LoggerFactory.getLogger(RedFlowerTask.class);

    private final RedFlowerService redFlowerService;

    public RedFlowerTask(RedFlowerService redFlowerService) {
        this.redFlowerService = redFlowerService;
    }

    /** 定时执行求红花批次。由 ScheduledTasks.runRedFlower 调用。 */
    public void runScheduled() {
        try {
            Long batchId = redFlowerService.runBatch("SCHEDULER");
            log.info("[B3] red flower batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[B3] red flower batch failed: {}", e.getMessage());
        }
    }
}
