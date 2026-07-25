package cn.net.rjnetwork.xianyu.manager.order.rate.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 求小红花批次日志 —— B3。
 * <p>每次定时任务跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=red_flower）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_red_flower_log")
public class ScheduledRedFlowerLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL */
    private String triggerSource;
    /** 计划送花目标数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如白名单外/今日上限已到） */
    private Integer skippedCount;
    /** 批次状态：RUNNING / SUCCESS / PARTIAL / FAILED */
    private String status;
    /** 批次开始时间 */
    private LocalDateTime startedAt;
    /** 批次结束时间 */
    private LocalDateTime endedAt;
    /** 失败原因聚合 */
    private String failureSummary;
    /** 关联的 batch_job.id；可空 */
    private Long batchJobId;
}
