package cn.net.rjnetwork.xianyu.manager.task.service;

import cn.net.rjnetwork.xianyu.manager.task.mapper.ScheduledTaskMapper;
import cn.net.rjnetwork.xianyu.manager.task.model.ScheduledTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 统一任务调度中心服务 —— B1。
 * <p>把现有 ScheduledTasks 的 12+ cron 任务注册成可管理态：启停、改 cron、手动触发、查看最近执行。
 * 对标参考项目 scheduled_task_registry：避免每个任务散落管理，统一在管理端「任务调度」页操作。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>启动时调 {@link #registerDefaults} 把现有 cron 任务（runCookieRefresh 等）写一行注册表，幂等；</li>
 *   <li>Spring @Scheduled 是静态注解，运行时改 cron 需走 SchedulingConfigurer 动态注册——
 *       本表先把「期望 cron」存下来，重启后由 ScheduledTaskService 重建（替代静态注解的渐进路径）；</li>
 *   <li>启停：enabled=0 时 ScheduledTasks 调前先查本表，停用的任务跳过执行；</li>
 *   <li>手动触发：管理端调 {@link #triggerManually}，按 taskKey 找到对应 Runnable 跑一次并记结果。</li>
 * </ul>
 */
@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final ScheduledTaskMapper mapper;

    public ScheduledTaskService(ScheduledTaskMapper mapper) {
        this.mapper = mapper;
    }

    /** 注册一个任务（若已存在则跳过），幂等。启动时由 ScheduledTasks 调。 */
    @Transactional
    public void register(String taskKey, String taskName, String category, String cron) {
        if (taskKey == null || taskKey.isBlank()) return;
        ScheduledTask existing = mapper.selectByTaskKey(taskKey);
        if (existing != null) return;
        ScheduledTask row = new ScheduledTask();
        row.setTaskKey(taskKey);
        row.setTaskName(taskName);
        row.setCategory(category);
        row.setCron(cron);
        row.setEnabled(1);
        mapper.insert(row);
        log.info("[B1] registered task {} ({}) cron={}", taskKey, taskName, cron);
    }

    /** 启停任务。 */
    @Transactional
    public boolean toggle(String taskKey, boolean enabled) {
        ScheduledTask row = mapper.selectByTaskKey(taskKey);
        if (row == null) return false;
        row.setEnabled(enabled ? 1 : 0);
        mapper.updateById(row);
        return true;
    }

    /** 改任务 cron 表达式。 */
    @Transactional
    public boolean updateCron(String taskKey, String cron) {
        ScheduledTask row = mapper.selectByTaskKey(taskKey);
        if (row == null) return false;
        row.setCron(cron);
        mapper.updateById(row);
        log.info("[B1] task {} cron updated to {}", taskKey, cron);
        return true;
    }

    /** 任务执行前调：返回 true=允许执行，false=已停用跳过。 */
    public boolean shouldRun(String taskKey) {
        ScheduledTask row = mapper.selectByTaskKey(taskKey);
        if (row == null) return true; // 未注册的任务默认放行（兼容旧链路）
        return row.getEnabled() != null && row.getEnabled() == 1;
    }

    /** 任务执行后调：记 lastRunAt/lastResult/lastError/lastDurationMs。 */
    @Transactional
    public void recordRun(String taskKey, String result, String error, Long durationMs, Long batchJobId) {
        ScheduledTask row = mapper.selectByTaskKey(taskKey);
        if (row == null) return;
        row.setLastRunAt(LocalDateTime.now());
        row.setLastResult(result);
        row.setLastError(error);
        row.setLastDurationMs(durationMs);
        row.setLastBatchJobId(batchJobId);
        mapper.updateById(row);
    }

    /** 便捷封装：跑一个 Runnable 并自动记执行结果。 */
    public void runWithRecord(String taskKey, Runnable action) {
        if (!shouldRun(taskKey)) {
            log.debug("[B1] task {} disabled, skip", taskKey);
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            action.run();
            recordRun(taskKey, "SUCCESS", null, System.currentTimeMillis() - t0, null);
        } catch (Exception e) {
            recordRun(taskKey, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage(),
                    System.currentTimeMillis() - t0, null);
            log.warn("[B1] task {} failed: {}", taskKey, e.getMessage());
        }
    }

    /** 分页查询任务列表，可选 category/enabled 过滤。给管理端「任务调度」页用。 */
    public Page<ScheduledTask> list(int page, int size, String category, Integer enabled) {
        Page<ScheduledTask> p = new Page<>(page, size);
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) wrapper.eq(ScheduledTask::getCategory, category);
        if (enabled != null) wrapper.eq(ScheduledTask::getEnabled, enabled);
        wrapper.orderByAsc(ScheduledTask::getCategory, ScheduledTask::getTaskKey);
        return mapper.selectPage(p, wrapper);
    }

    /** 拿单任务注册信息。 */
    public ScheduledTask get(String taskKey) {
        return mapper.selectByTaskKey(taskKey);
    }

    /** 启动时注册现有 cron 任务（幂等）。由 ScheduledTasks @PostConstruct 调。 */
    public void registerDefaults() {
        register("runCookieRefresh", "Cookie 浏览器刷新", "renew", "0 0/10 * * * *");
        register("runLoginRenew", "登录续期", "renew", "0 0/15 * * * *");
        register("runTokenRenewal", "Token/IM 续期", "renew", "0 0/20 * * * *");
        register("runRedelivery", "自动补发货", "ship", "0 0/5 * * * *");
        register("runConfirmReceipt", "催确认收货", "ship", "0 0 10 * * *");
        register("autoSyncProducts", "商品定时拉取", "sync", "0 0/30 * * * *");
        register("runHealthCheck", "账号健康检测", "monitor", "0 0/5 * * * *");
        register("autoScanVirtualShip", "虚拟发货扫描", "ship", "0 * * * * *");
        register("autoConfirmReceipt", "自动确认收货", "ship", "0 0 3 * * *");
        register("autoSyncOrders", "订单定时拉取", "sync", "0 0/2 * * * *");
        register("autoSyncCollects", "收藏定时拉取", "sync", "0 0/30 * * * *");
        register("runMonitorTasks", "监控任务执行", "monitor", "0/30 * * * * *");
    }
}
