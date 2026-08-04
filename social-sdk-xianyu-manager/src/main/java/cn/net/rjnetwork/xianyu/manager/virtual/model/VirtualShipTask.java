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

    /** PENDING / PROCESSING / SENT_PENDING_ACK / SUCCESS / FAILED / SKIPPED / RETRY_EXHAUSTED */
    private String status;

    private Integer retryCount;

    /** A9 补发最大重试次数上限，默认 5；retryCount >= maxRetry 则不再重试，转人工介入 */
    private Integer maxRetry;

    private String errorMessage;

    /** 到达该时间后才允许执行，用于延迟发货 */
    private java.time.LocalDateTime executeAt;

    private java.time.LocalDateTime processedAt;

    /**
     * 发货消息对应的闲鱼 IM 帧 mid（sendByReceiverScope 发出时本地生成）。
     * 服务端送达回执帧会带同一 mid，据此匹配把 SENT_PENDING_ACK → SUCCESS。
     * 空表示未发出或无需回执（重试只补 dummyDelivery 的后续轮次）。
     */
    private String messageId;

    /** 发货消息发出时间（SENT_PENDING_ACK 起算点，超时未收 ack 由 ShipAckTimeoutTask 转 FAILED）。 */
    private java.time.LocalDateTime sentAt;
}
