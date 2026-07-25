package cn.net.rjnetwork.xianyu.manager.product.polish.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时擦亮批次日志 —— B4。
 * <p>每次定时擦亮批量跑完写一行批次；明细走 B9 {@code batch_job/batch_job_item}（job_type=polish）。
 * 闲鱼侧擦亮走 {@link cn.net.rjnetwork.xianyu.api.XianyuItemAuxApiService#editItem}（编辑商品触发擦亮，
 * 对标参考项目 polish_service.py）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scheduled_polish_log")
public class ScheduledPolishLog extends BaseEntity {

    /** 触发来源：SCHEDULER / MANUAL */
    private String triggerSource;
    /** 账号 ID；null=多账号批次 */
    private Long accountId;
    /** 计划擦亮商品数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failedCount;
    /** 跳过数（如商品已下架/熔断中） */
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
