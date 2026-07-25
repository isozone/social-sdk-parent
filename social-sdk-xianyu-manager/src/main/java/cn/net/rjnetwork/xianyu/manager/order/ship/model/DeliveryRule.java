package cn.net.rjnetwork.xianyu.manager.order.ship.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发货匹配规则 —— BOT-O3。
 * <p>不同于 {@link DeliveryBlockRule}（A7 拦截阻断发货），本表是 <b>匹配</b> 发货：
 * 买家消息/订单关键词 → 命中卡券（cardId），决定发哪张卡、发几次。</p>
 *
 * <p>语义对标 xianyu-auto-bot 的 delivery_rules 表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("delivery_rules")
public class DeliveryRule extends BaseEntity {

    /** 账号 ID；null=全局规则 */
    private Long accountId;
    /** 商品 ID；null=不限商品 */
    private String itemId;
    /** 匹配关键词（多个用英文逗号分隔，命中任一即匹配） */
    private String keyword;
    /** 匹配模式：CONTAINS（包含任一关键词）/EXACT（完全相等）/REGEX（正则） */
    private String matchMode;
    /** 命中后发哪张卡券（ship_card.id） */
    private Long cardId;
    /** 命中后发几张（默认 1） */
    private Integer deliveryCount;
    /** 优先级（数值越小越先匹配） */
    private Integer priority;
    /** 是否启用：1=是 0=否 */
    private Integer enabled;
    /** 最近命中时间 */
    private LocalDateTime lastHitAt;
    /** 命中次数（统计用） */
    private Integer hitCount;
    /** 备注 */
    private String remark;
}
