package cn.net.rjnetwork.xianyu.manager.virtual.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发货终态 —— BOT-O5。
 * <p>多数量订单按 unit 跟踪 sent/finalized；支持部分成功。
 * 主键复合：order_id + unit_index（quantity=3 时 3 个 unit 状态独立）。</p>
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>PENDING — 待发货</li>
 *   <li>SENT — 已发卡券，待买家确认</li>
 *   <li>FINALIZED — 已终态（买家确认/超时自动确认/退款关闭）</li>
 *   <li>FAILED — 发货失败</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("delivery_finalization")
public class DeliveryFinalization extends BaseEntity {

    /** 订单 ID */
    private Long orderId;
    /** 多件中的第几件（从 0 开始） */
    private Integer unitIndex;
    /** 总件数（冗余，便于查询） */
    private Integer totalUnits;
    /** 关联卡券 ID（发货时占用） */
    private Long cardId;
    /** 关联卡密 ID（data_card_reservation.reservation_id） */
    private String reservationId;
    /** 状态：PENDING/SENT/FINALIZED/FAILED */
    private String status;
    /** 发货日志 ID（delivery_log.id） */
    private Long deliveryLogId;
    /** 终态时间（FINALIZED/FAILED） */
    private LocalDateTime finalizedAt;
    /** 失败/终态原因 */
    private String reason;
}
