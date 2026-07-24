package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Cookie 浏览器刷新批次日志 —— A1。
 * <p>每次定时任务跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=cookies_refresh）。
 * 本表只存批次级聚合，便于管理端「Cookie 刷新日志」页直接查询，不必关联 batch_job。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_cookies_refresh_log")
public class ScheduledCookiesRefreshLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL / SYSTEM（健康检测失效触发） */
    private String triggerSource;
    /** 计划执行账号数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如冷却中、计划已停用） */
    private Integer skippedCount;
    /** 批次状态：RUNNING / SUCCESS / PARTIAL / FAILED */
    private String status;
    /** 批次开始时间 */
    private LocalDateTime startedAt;
    /** 批次结束时间 */
    private LocalDateTime endedAt;
    /** 失败原因聚合 */
    private String failureSummary;
    /** 关联的 batch_job.id，便于查明细；可空 */
    private Long batchJobId;
}
