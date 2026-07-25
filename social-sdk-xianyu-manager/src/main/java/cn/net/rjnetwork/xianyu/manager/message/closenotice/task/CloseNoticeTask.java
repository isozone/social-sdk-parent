package cn.net.rjnetwork.xianyu.manager.message.closenotice.task;

import cn.net.rjnetwork.xianyu.manager.message.closenotice.service.CloseNoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 定时关闭平台通知任务入口 —— B5。
 * <p>由 ScheduledTasks 统一调度（cron 每 1 小时一次），调 CloseNoticeService.runBatch 执行批次。</p>
 */
@Component
public class CloseNoticeTask {

    private static final Logger log = LoggerFactory.getLogger(CloseNoticeTask.class);

    private final CloseNoticeService closeNoticeService;

    public CloseNoticeTask(CloseNoticeService closeNoticeService) {
        this.closeNoticeService = closeNoticeService;
    }

    /** 定时执行关闭通知批次。由 ScheduledTasks.runCloseNotice 调用。 */
    public void runScheduled() {
        try {
            Long batchId = closeNoticeService.runBatch("SCHEDULER");
            log.info("[B5] close notice batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[B5] close notice batch failed: {}", e.getMessage());
        }
    }
}
