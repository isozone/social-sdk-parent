package cn.net.rjnetwork.xianyu.manager.account.renew.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录续期批次日志 —— A3。
 * <p>每次定时任务跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=login_renew）。
 * 本表只存批次级聚合，便于管理端「登录续期日志」页直接查询。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_login_renew_log")
public class ScheduledLoginRenewLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL / SYSTEM（A1+A2 失效触发） */
    private String triggerSource;
    /** 计划执行账号数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如计划已停用、仍在冷却中） */
    private Integer skippedCount;
    /** 等待扫码数（QR 登录推二维码后待用户扫码） */
    private Integer waitingQrCount;
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
