package cn.net.rjnetwork.xianyu.manager.order.ship.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 自动发货拦截规则 —— A7。
 * <p>按维度拦截不该自动发货的订单，避免规则误发：黑名单买家/低信誉/特定商品/特定价格区间等。
 * 引擎在发货主链路（A8）每步前调 {@link cn.net.rjnetwork.xianyu.manager.order.ship.service.DeliveryRuleEngine#shouldBlock}
 * 命中任一启用规则即跳过发货并记 SKIPPED + 触发通知。</p>
 *
 * <p>规则维度（ruleType）：</p>
 * <ul>
 *   <li>BUYER_BLACK：买家黑名单（buyerId 命中即拦）</li>
 *   <li>BUYER_LOW_CREDIT：买家信誉过低（credibility_score &lt; 阈值）</li>
 *   <li>PRODUCT_BLOCK：特定商品不自动发（productId 命中即拦）</li>
 *   <li>PRICE_RANGE：价格区间拦截（min/max 之外拦，防薅羊毛）</li>
 *   <li>REGION_BLOCK：特定地区不发货（buyerRegion 命中即拦）</li>
 *   <li>TIME_WINDOW：时间窗口拦截（如夜间不发，避免被风控盯上）</li>
 * </ul>
 *
 * <p>规则参数走 ruleParams（JSON 字符串），引擎按 ruleType 解析：
 * 如 PRICE_RANGE={"min":0.01,"max":100}，BUYER_BLACK={"buyerIds":["u1","u2"]}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("delivery_block_rule")
public class DeliveryBlockRule extends BaseEntity {

    /** 账号 ID；null=全局规则 */
    private Long accountId;
    /** 规则类型：BUYER_BLACK/BUYER_LOW_CREDIT/PRODUCT_BLOCK/PRICE_RANGE/REGION_BLOCK/TIME_WINDOW */
    private String ruleType;
    /** 规则参数（JSON 字符串，按 ruleType 解析） */
    private String ruleParams;
    /** 规则名称（人类可读） */
    private String ruleName;
    /** 优先级（数字越小越先评估） */
    private Integer priority;
    /** 启用开关：0=停用 1=启用 */
    private Integer enabled;
    /** 命中后动作：BLOCK（拦截不发）/ NOTIFY_ONLY（仅通知仍发）/ DELAY（延迟发货） */
    private String action;
    /** 命中通知模板（可空，使用默认） */
    private String notifyTemplate;
    /** 最近命中时间（引擎每次命中更新，便于诊断） */
    private LocalDateTime lastHitAt;
    /** 命中次数（引擎每次命中自增，便于诊断规则是否过热） */
    private Integer hitCount;
}
