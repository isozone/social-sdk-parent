package cn.net.rjnetwork.xianyu.manager.product.polish.task;

import cn.net.rjnetwork.xianyu.manager.product.polish.service.PolishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 定时擦亮任务入口 —— B4。
 * <p>由 ScheduledTasks 统一调度（cron 每 2 小时一次），调 PolishService.runBatch 执行批次。
 * 扫账号在售商品调 editItem 触发擦亮，提升商品在闲鱼搜索排序。</p>
 */
@Component
public class PolishTask {

    private static final Logger log = LoggerFactory.getLogger(PolishTask.class);

    private final PolishService polishService;

    public PolishTask(PolishService polishService) {
        this.polishService = polishService;
    }

    /** 定时执行擦亮批次。由 ScheduledTasks.runPolish 调用。 */
    public void runScheduled() {
        try {
            Long batchId = polishService.runBatch("SCHEDULER");
            log.info("[B4] polish batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[B4] polish batch failed: {}", e.getMessage());
        }
    }
}
