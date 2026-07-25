package cn.net.rjnetwork.xianyu.manager.virtual.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 自动发货任务实体（定时扫描执行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("virtual_ship_task")
public class VirtualShipTask extends BaseEntity {

    /** 账号 ID（A8 主链路用，关联 xianyu_account.id） */
    private Long accountId;

    private Long orderId;

    private Long productId;

    /** PENDING / PROCESSING / SHIPPED / FAILED / SKIPPED */
    private String status;

    private Integer retryCount;

    /** A9 补发最大重试次数上限，默认 5；retryCount >= maxRetry 则不再重试，转人工介入 */
    private Integer maxRetry;

    private String errorMessage;

    /** 到达该时间后才允许执行，用于延迟发货 */
    private java.time.LocalDateTime executeAt;

    private java.time.LocalDateTime processedAt;
}
