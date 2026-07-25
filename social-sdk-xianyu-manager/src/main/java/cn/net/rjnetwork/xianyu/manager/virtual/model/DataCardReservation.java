package cn.net.rjnetwork.xianyu.manager.virtual.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 卡密预留 —— BOT-O6。
 * <p>发货前预留卡密 → 发送成功确认消耗 → 失败释放。
 * 唯一约束 (card_id, reserved_for, status) 防并发双花。</p>
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>RESERVED — 已预留，待发货确认</li>
 *   <li>CONSUMED — 已消耗（发货成功）</li>
 *   <li>RELEASED — 已释放（发货失败/超时）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_card_reservations")
public class DataCardReservation extends BaseEntity {

    /** 关联卡券 ID（ship_card.id 或 card_id） */
    private Long cardId;
    /** 关联卡密 ID（data_card.id） */
    private Long dataCardId;
    /** 预留给哪个订单 */
    private Long reservedFor;
    /** 预留给哪个买家 */
    private String buyerId;
    /** 状态：RESERVED/CONSUMED/RELEASED */
    private String status;
    /** 预留时间 */
    private LocalDateTime reservedAt;
    /** 确认/释放时间 */
    private LocalDateTime confirmedAt;
    /** 释放/失败原因 */
    private String reason;
}
