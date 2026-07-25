package cn.net.rjnetwork.xianyu.manager.message.closenotice.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时关闭平台通知批次日志 —— B5。
 * <p>每次定时关闭通知批量跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=close_notice）。
 * 闲鱼侧关闭通知走 {@link cn.net.rjnetwork.xianyu.api.XianyuApiFacade#closeNotice}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_close_notice_log")
public class ScheduledCloseNoticeLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL */
    private String triggerSource;
    /** 账号 ID；null=多账号批次 */
    private Long accountId;
    /** 计划关闭通知数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如账号熔断中） */
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
