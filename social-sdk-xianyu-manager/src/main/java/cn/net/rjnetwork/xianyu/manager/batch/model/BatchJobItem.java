package cn.net.rjnetwork.xianyu.manager.batch.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次明细 —— 一次 {@link BatchJob} 下每条执行单元的结果。
 * <p>关联 batch_id 到 BatchJob；item_key 携带具体目标标识（如 accountId、orderId、productId），
 * 状态/耗时/失败原因字段便于管理端检索与诊断。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("batch_job_item")
public class BatchJobItem extends BaseEntity {

    /** 所属批次 ID */
    private Long batchId;
    /** 目标标识，如 accountId / orderId / productId */
    private String itemKey;
    /** 目标显示名，如账号名、商品标题，便于日志阅读 */
    private String itemLabel;
    /** 明细状态：SUCCESS / FAILED / SKIPPED / RETRYING */
    private String status;
    /** 本条耗时（毫秒） */
    private Long durationMs;
    /** 失败原因简述 */
    private String failureReason;
    /** 详细上下文（如完整异常栈、MTOP 原始返回），TEXT 字段 */
    private String detail;
    /** 该条执行开始时间 */
    private LocalDateTime startedAt;
    /** 该条执行结束时间 */
    private LocalDateTime endedAt;
}
