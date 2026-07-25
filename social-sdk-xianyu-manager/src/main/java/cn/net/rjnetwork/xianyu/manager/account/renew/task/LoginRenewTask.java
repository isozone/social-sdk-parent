package cn.net.rjnetwork.xianyu.manager.account.renew.task;

import cn.net.rjnetwork.xianyu.manager.account.renew.service.LoginRenewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 登录续期定时任务入口 —— A3。
 * <p>由 ScheduledTasks 统一调度（cron 每 15 分钟一次），调 LoginRenewService.runBatch 执行批次。
 * 触发条件：A1（浏览器刷新）+ A2（API 续期）双双失效 → 熔断器 OPEN → 启动 A3。</p>
 */
@Component
public class LoginRenewTask {

    private static final Logger log = LoggerFactory.getLogger(LoginRenewTask.class);

    private final LoginRenewService loginRenewService;

    public LoginRenewTask(LoginRenewService loginRenewService) {
        this.loginRenewService = loginRenewService;
    }

    /** 定时执行登录续期批次。由 ScheduledTasks.runLoginRenew 调用。 */
    public void runScheduled() {
        try {
            Long batchId = loginRenewService.runBatch("SCHEDULER");
            log.info("[A3] login renew batch done, batchId={}", batchId);
        } catch (Exception e) {
            log.warn("[A3] login renew batch failed: {}", e.getMessage());
        }
    }
}
