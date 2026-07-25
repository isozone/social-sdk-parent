package cn.net.rjnetwork.xianyu.manager.task.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 统一任务调度中心注册表 —— B1。
 * <p>把现有 ScheduledTasks 的 12 个 cron 任务（cookies_refresh / login_renew / token_renewal /
 * redelivery / confirm_receipt / sync_products / health_check / virtual_ship / monitor / sync_orders /
 * sync_collects / rate / red_flower / polish / close_notice / db_backup …）注册成可管理态：
 * 启停、改 cron、手动触发、查看最近执行。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>taskKey 唯一，对应 ScheduledTasks 的方法名（如 runCookieRefresh）；</li>
 *   <li>cron 字段可改，但 Spring @Scheduled 是静态注解，运行时改 cron 需走 SchedulingConfigurer 动态注册——
 *       本表先把「期望 cron」存下来，启动时由 ScheduledTaskService 重建动态任务（替代静态注解）；</li>
 *   <li>enabled=0 时跳过；lastRunAt/lastResult/lastError 给管理端诊断用。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_task")
public class ScheduledTask extends BaseEntity {

    /** 任务唯一 key，对应 ScheduledTasks 方法名（如 runCookieRefresh） */
    private String taskKey;
    /** 任务显示名（如「Cookie 浏览器刷新」） */
    private String taskName;
    /** 任务分类：renew / ship / sync / monitor / maintenance */
    private String category;
    /** cron 表达式（可改，启动时由 ScheduledTaskService 动态注册） */
    private String cron;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 上次执行时间 */
    private LocalDateTime lastRunAt;
    /** 上次执行结果：SUCCESS / FAILED / SKIPPED */
    private String lastResult;
    /** 上次失败原因 */
    private String lastError;
    /** 上次执行耗时（毫秒） */
    private Long lastDurationMs;
    /** 关联的 batch_job.id（若任务走 B9 批次）；可空 */
    private Long lastBatchJobId;
}
