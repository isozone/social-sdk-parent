package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Token/IM 续期批次日志 —— A4。
 * <p>每次定时任务跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=token_renewal）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_token_renewal_log")
public class ScheduledTokenRenewalLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL / SYSTEM（消息同步触发） */
    private String triggerSource;
    /** 计划执行账号数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如 token 仍有效） */
    private Integer skippedCount;
    /** 触发滑块解题数（被风控拦截走滑块刷新 x5sec） */
    private Integer captchaTriggeredCount;
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
