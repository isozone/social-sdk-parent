package cn.net.rjnetwork.xianyu.manager.task;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.task.AccountHealthTask;
import cn.net.rjnetwork.xianyu.manager.account.renew.task.CookiesRefreshTask;
import cn.net.rjnetwork.xianyu.manager.account.renew.task.LoginRenewTask;
import cn.net.rjnetwork.xianyu.manager.account.renew.task.TokenRenewalTask;
import cn.net.rjnetwork.xianyu.manager.message.service.ImMessageWatcherService;
import cn.net.rjnetwork.xianyu.manager.virtual.task.ConfirmReceiptTask;
import cn.net.rjnetwork.xianyu.manager.virtual.task.RedeliveryTask;
import cn.net.rjnetwork.xianyu.manager.task.service.ScheduledTaskService;
import cn.net.rjnetwork.xianyu.manager.order.rate.task.AutoRateTask;
import cn.net.rjnetwork.xianyu.manager.order.rate.task.RedFlowerTask;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import cn.net.rjnetwork.xianyu.manager.virtual.service.VirtualShipService;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderSyncService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorTaskService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorTaskRunner;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import cn.net.rjnetwork.xianyu.manager.collect.service.CollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final AccountMapper accountMapper;
    private final ProductService productService;
    private final MonitorService monitorService;
    private final AccountHealthTask healthTask;
    private final ImMessageWatcherService watcherService;
    private final VirtualShipService virtualShipService;
    private final OrderSyncService orderSyncService;
    private final MonitorTaskService monitorTaskService;
    private final MonitorTaskRunner monitorTaskRunner;
    private final CollectService collectService;
    private final CookiesRefreshTask cookiesRefreshTask;
    private final LoginRenewTask loginRenewTask;
    private final TokenRenewalTask tokenRenewalTask;
    private final RedeliveryTask redeliveryTask;
    private final ConfirmReceiptTask confirmReceiptTask;
    private final ScheduledTaskService scheduledTaskService;
    private final AutoRateTask autoRateTask;
    private final RedFlowerTask redFlowerTask;

    public ScheduledTasks(AccountMapper accountMapper, ProductService productService,
                          MonitorService monitorService, AccountHealthTask healthTask,
                          ImMessageWatcherService watcherService,
                          VirtualShipService virtualShipService,
                          OrderSyncService orderSyncService,
                          MonitorTaskService monitorTaskService,
                          MonitorTaskRunner monitorTaskRunner,
                          CollectService collectService,
                          CookiesRefreshTask cookiesRefreshTask,
                          LoginRenewTask loginRenewTask,
                          TokenRenewalTask tokenRenewalTask,
                          RedeliveryTask redeliveryTask,
                          ConfirmReceiptTask confirmReceiptTask,
                          ScheduledTaskService scheduledTaskService,
                          AutoRateTask autoRateTask,
                          RedFlowerTask redFlowerTask) {
        this.accountMapper = accountMapper;
        this.productService = productService;
        this.monitorService = monitorService;
        this.healthTask = healthTask;
        this.watcherService = watcherService;
        this.virtualShipService = virtualShipService;
        this.orderSyncService = orderSyncService;
        this.monitorTaskService = monitorTaskService;
        this.monitorTaskRunner = monitorTaskRunner;
        this.collectService = collectService;
        this.cookiesRefreshTask = cookiesRefreshTask;
        this.loginRenewTask = loginRenewTask;
        this.tokenRenewalTask = tokenRenewalTask;
        this.redeliveryTask = redeliveryTask;
        this.confirmReceiptTask = confirmReceiptTask;
        this.scheduledTaskService = scheduledTaskService;
        this.autoRateTask = autoRateTask;
        this.redFlowerTask = redFlowerTask;
        // B1：启动时把现有 cron 任务幂等注册到 scheduled_task 表，便于管理端启停/改 cron/查最近执行
        try { scheduledTaskService.registerDefaults(); }
        catch (Exception e) { log.warn("[B1] registerDefaults failed (non-fatal): {}", e.getMessage()); }
    }

    // ======================== Cookie 浏览器刷新定时链路（A1） ========================

    /** 每 10 分钟扫描刷新计划到期 / Cookie 失效的账号，启动 Chrome 容器刷新 Cookie。
     *  默认 onlyExpiredOnly=true：仅刷新健康检测失效的账号，避免无谓浏览器启动。
     */
    @Scheduled(cron = "0 0/10 * * * *")
    public void runCookieRefresh() {
        cookiesRefreshTask.runScheduled();
    }

    // ======================== 登录续期定时链路（A3） ========================

    /** 每 15 分钟扫描登录续期计划到期 / 重试次数未耗尽的账号，走扫码/密码登录流程拿新 Cookie。
     *  触发条件：A1（浏览器刷新）+ A2（API 续期）双双失效 → 熔断器 OPEN → 启动 A3。
     */
    @Scheduled(cron = "0 0/15 * * * *")
    public void runLoginRenew() {
        loginRenewTask.runScheduled();
    }

    // ======================== Token/IM 续期定时链路（A4） ========================

    /** 每 20 分钟扫描 im_token_cache 续期到期 / 失效的账号，调 MTOP pc.login.token 拿新 token；
     *  被风控 punish 则联动滑块（captchaSolver.solve）刷新 x5sec，写回缓存 + 账号 imCookieHeader。
     */
    @Scheduled(cron = "0 0/20 * * * *")
    public void runTokenRenewal() {
        tokenRenewalTask.runScheduled();
    }

    // ======================== 自动补发货定时链路（A9） ========================

    /** 每 5 分钟扫 FAILED/SKIPPED 但未超重试上限的 virtual_ship_task，按指数退避重跑 A8 主链路。
     *  重试耗尽（retryCount >= maxRetry）则标 RETRY_EXHAUSTED 转人工介入。
     */
    @Scheduled(cron = "0 0/5 * * * *")
    public void runRedelivery() {
        redeliveryTask.runScheduled();
    }

    // ======================== 自动确认收货定时链路（A10） ========================

    /** 每天 10:00 扫已发货 N 天（autoConfirmDays）但买家未确认的订单，按 confirmReceiptMessage 模板发话术催确认。
     *  闲鱼侧 SDK 无专门「确认收货」MTOP API，走消息话术催确认（轻量可行）。
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void runConfirmReceipt() {
        confirmReceiptTask.runScheduled();
    }

    // ======================== 自动评价定时链路（B2） ========================

    /** 每天 11:00 扫已收货 N 天（delayDays）且卖家未评的订单，按 AutoRateConfig 调 reviewOrder 评好评。
     *  话术模板支持 {itemTitle}/{buyerNick}/{accountName} 占位符替换。
     */
    @Scheduled(cron = "0 0 11 * * *")
    public void runAutoRate() {
        autoRateTask.runScheduled();
    }

    // ======================== 求小红花定时链路（B3） ========================

    /** 每天 09:00 给已成交订单买家送红花提信誉，每日送花上限（dailyLimit）防风控盯上。
     *  闲鱼侧送红花走 XianyuApiFacade.sendRedFlower（mtop.taobao.idlemessage.red.flower）。
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void runRedFlower() {
        redFlowerTask.runScheduled();
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void autoSyncProducts() {
        log.info("[Schedule] auto-sync products start");
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        for (XianyuAccount acc : accounts) {
            try {
                ProductService.SyncResult r = productService.syncFromXianyu(acc.getId());
                log.info("[Schedule] sync account {}: synced={} ins={} upd={}",
                        acc.getId(), r.synced, r.inserted, r.updated);
            } catch (Exception e) {
                log.warn("[Schedule] sync failed account {}: {}", acc.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0/5 * * * *")
    public void runHealthCheck() {
        healthTask.checkAccountHealth();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void hourlyStats() {
        try {
            monitorService.invalidateCache();
            log.info("[Schedule] stats cache refreshed");
        } catch (Exception e) {
            log.warn("[Schedule] stats failed: {}", e.getMessage());
        }
    }

    // ======================== 虚拟发货定时链路 ========================

    /** 每分钟扫描待发货的虚拟订单，调 VirtualShipService.scanAndShip 自动发货 */
    @Scheduled(cron = "0 * * * * *")
    public void autoScanVirtualShip() {
        try {
            virtualShipService.scanAndShip();
        } catch (Exception e) {
            log.warn("[Schedule] virtual scanAndShip failed: {}", e.getMessage());
        }
    }

    /** 每 5 分钟重试 FAILED 的发货任务（最多 retry_count 上限由 service 控制） */
    @Scheduled(cron = "0 0/5 * * * *")
    public void retryFailedShipTasks() {
        try {
            virtualShipService.retryFailedShipTasks();
        } catch (Exception e) {
            log.warn("[Schedule] virtual retryFailed failed: {}", e.getMessage());
        }
    }

    /** 每天凌晨 3 点扫描超期未确认收货的订单，自动确认（auto_confirm_days 由配置控制） */
    @Scheduled(cron = "0 0 3 * * *")
    public void autoConfirmReceipt() {
        try {
            virtualShipService.autoConfirmReceipt();
            log.info("[Schedule] virtual autoConfirmReceipt done");
        } catch (Exception e) {
            log.warn("[Schedule] virtual autoConfirmReceipt failed: {}", e.getMessage());
        }
    }

    // ======================== 订单同步定时链路 ========================

    /** 每 2 分钟拉一次闲鱼订单（BOUGHT+SOLD），同步入库并触发 NEW_ORDER 通知 */
    @Scheduled(cron = "0 0/2 * * * *")
    public void autoSyncOrders() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        for (XianyuAccount acc : accounts) {
            try {
                OrderSyncService.SyncResult r = orderSyncService.syncOrders(acc.getId());
                if (r.success) {
                    log.info("[Schedule] sync orders account {}: bought={}, sold={}",
                            acc.getId(), r.boughtCount, r.soldCount);
                } else {
                    log.warn("[Schedule] sync orders account {} failed: {}", acc.getId(), r.message);
                }
            } catch (Exception e) {
                log.warn("[Schedule] sync orders account {} error: {}", acc.getId(), e.getMessage());
            }
        }
    }

    // ======================== 收藏同步定时链路 ========================

    /** 每 30 分钟同步所有账号的收藏列表 */
    @Scheduled(cron = "0 0/30 * * * *")
    public void autoSyncCollects() {
        try {
            int count = collectService.syncAllAccounts();
            log.info("[Schedule] auto-sync collects done, synced={}", count);
        } catch (Exception e) {
            log.warn("[Schedule] auto-sync collects failed: {}", e.getMessage());
        }
    }

    // ======================== 监控任务定时链路 ========================

    /** 每 30 秒扫描到期监控任务（getDueTasks），逐个执行（executeTask） */
    @Scheduled(cron = "0/30 * * * * *")
    public void runMonitorTasks() {
        try {
            List<MonitorTask> due = monitorTaskService.getDueTasks(50);
            if (due == null || due.isEmpty()) return;
            log.info("[Schedule] monitor due tasks: {}", due.size());
            for (MonitorTask task : due) {
                try {
                    monitorTaskRunner.executeTask(task);
                } catch (Exception e) {
                    log.warn("[Schedule] monitor task {} failed: {}", task.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Schedule] runMonitorTasks failed: {}", e.getMessage());
        }
    }

    @EventListener
    public void onNotifyEvent(NotifyEvent event) {
        if ("NEW_MESSAGE".equals(event.getType())) {
            log.info("[Notify] new message: {}", event.getPayload());
        }
    }
}
