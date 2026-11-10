package cn.net.rjnetwork.xianyu.manager.account.renew.task;

import cn.net.rjnetwork.xianyu.manager.account.renew.service.CookieRenewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cookie 浏览器刷新定时任务入口 —— A1。
 * <p>由 ScheduledTasks 统一调度（cron 每 10 分钟一次），调 CookieRenewService.runBatch 执行批次。
 * 默认 onlyExpiredOnly=true：仅刷新健康检测失效的账号，避免无谓浏览器启动。</p>
 */
@Component
public class CookiesRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(CookiesRefreshTask.class);

    private final CookieRenewService cookieRenewService;

    public CookiesRefreshTask(CookieRenewService cookieRenewService) {
        this.cookieRenewService = cookieRenewService;
    }

    /**
     * 定时执行 Cookie 浏览器刷新批次。
     * 由 ScheduledTasks.runCookieRefresh 调用，避免本类直接挂 @Scheduled 导致重复扫描。
     */
    public void runScheduled() {
        try {
            // onlyExpiredOnly=false：把筛选交给计划字段——onlyOnExpired=1 的健康账号在 renewOne 内判 SKIPPED，
            // onlyOnExpired=0 的保活计划则按 intervalMinutes 定期强制刷新（否则保活模式永远不会被定时触发）
            Long batchId = cookieRenewService.runBatch("SCHEDULER", false);
            log.info("[A1] cookies refresh batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[A1] cookies refresh batch failed: {}", e.getMessage());
        }
    }
}
