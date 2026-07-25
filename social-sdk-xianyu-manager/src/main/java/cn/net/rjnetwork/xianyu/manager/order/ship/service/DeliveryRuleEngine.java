package cn.net.rjnetwork.xianyu.manager.order.ship.service;

import cn.net.rjnetwork.xianyu.manager.order.ship.mapper.DeliveryBlockRuleMapper;
import cn.net.rjnetwork.xianyu.manager.order.ship.model.DeliveryBlockRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动发货规则引擎 —— A7。
 * <p>给 A8 自动发货主链路在每步前调 {@link #shouldBlock} 评估拦截规则，
 * 命中任一启用规则即返回 {@link BlockDecision#block}，主链路跳过发货并记 SKIPPED + 触发通知。
 * 规则按 priority 升序评估，首个命中的规则决定动作（BLOCK / NOTIFY_ONLY / DELAY）。</p>
 *
 * <p>支持维度（{@link DeliveryBlockRule#getRuleType}）：</p>
 * <ul>
 *   <li>BUYER_BLACK：买家黑名单（ruleParams={"buyerIds":["u1","u2"]}）</li>
 *   <li>BUYER_LOW_CREDIT：买家信誉过低（ruleParams={"minCredit":80}，需查 buyer_profile 表）</li>
 *   <li>PRODUCT_BLOCK：特定商品不自动发（ruleParams={"productIds":[1,2]}）</li>
 *   <li>PRICE_RANGE：价格区间拦截（ruleParams={"min":0.01,"max":100}，防薅羊毛）</li>
 *   <li>REGION_BLOCK：特定地区不发货（ruleParams={"regions":["新疆","西藏"]}）</li>
 *   <li>TIME_WINDOW：时间窗口拦截（ruleParams={"start":"00:00","end":"06:00"}，夜间不发）</li>
 * </ul>
 */
@Service
public class DeliveryRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRuleEngine.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DeliveryBlockRuleMapper ruleMapper;

    public DeliveryRuleEngine(DeliveryBlockRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 评估发货拦截规则。
     * @param context 发货上下文（accountId/buyerId/buyerCredit/productId/price/region/time）
     * @return BlockDecision：block=是否拦截，action=动作，ruleName=命中规则名，reason=原因
     */
    public BlockDecision shouldBlock(DeliveryContext context) {
        List<DeliveryBlockRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<DeliveryBlockRule>()
                .eq(DeliveryBlockRule::getEnabled, 1)
                .and(w -> w.isNull(DeliveryBlockRule::getAccountId)
                        .or().eq(DeliveryBlockRule::getAccountId, context.accountId))
                .orderByAsc(DeliveryBlockRule::getPriority));
        for (DeliveryBlockRule rule : rules) {
            if (matches(rule, context)) {
                markHit(rule);
                return new BlockDecision(true, rule.getAction(), rule.getRuleName(),
                        "命中规则 " + rule.getRuleType() + ": " + rule.getRuleName());
            }
        }
        return new BlockDecision(false, null, null, null);
    }

    /** 评估单条规则是否命中。 */
    private boolean matches(DeliveryBlockRule rule, DeliveryContext ctx) {
        try {
            JsonNode params = rule.getRuleParams() == null || rule.getRuleParams().isBlank()
                    ? JSON.createObjectNode() : JSON.readTree(rule.getRuleParams());
            switch (rule.getRuleType()) {
                case "BUYER_BLACK":
                    return params.path("buyerIds").isArray()
                            && containsText(params.path("buyerIds"),
                                    ctx.buyerId == null ? null : String.valueOf(ctx.buyerId));
                case "BUYER_LOW_CREDIT":
                    int minCredit = params.path("minCredit").asInt(80);
                    return ctx.buyerCredit == null || ctx.buyerCredit < minCredit;
                case "PRODUCT_BLOCK":
                    return params.path("productIds").isArray()
                            && containsLong(params.path("productIds"), ctx.productId);
                case "PRICE_RANGE":
                    double min = params.path("min").asDouble(Double.NEGATIVE_INFINITY);
                    double max = params.path("max").asDouble(Double.POSITIVE_INFINITY);
                    return ctx.price == null || ctx.price < min || ctx.price > max;
                case "REGION_BLOCK":
                    if (ctx.region == null || ctx.region.isBlank()) return false;
                    return containsText(params.path("regions"), ctx.region);
                case "TIME_WINDOW":
                    LocalTime now = Optional.ofNullable(ctx.time).orElse(LocalTime.now());
                    LocalTime start = LocalTime.parse(params.path("start").asText("00:00"));
                    LocalTime end = LocalTime.parse(params.path("end").asText("06:00"));
                    // 跨日窗口（start > end）如 23:00-05:00 需特殊处理
                    return start.isAfter(end) ? (now.isAfter(start) || now.isBefore(end))
                            : (!now.isBefore(start) && now.isBefore(end));
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("[A7] rule {} params parse failed: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    private boolean containsText(JsonNode arr, String value) {
        if (value == null) return false;
        for (JsonNode n : arr) if (value.equals(n.asText())) return true;
        return false;
    }

    private boolean containsLong(JsonNode arr, Long value) {
        if (value == null) return false;
        for (JsonNode n : arr) if (value == n.asLong()) return true;
        return false;
    }

    /** 命中后更新命中次数 + 最近命中时间，便于管理端诊断。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markHit(DeliveryBlockRule rule) {
        rule.setLastHitAt(LocalDateTime.now());
        rule.setHitCount(Optional.ofNullable(rule.getHitCount()).orElse(0) + 1);
        ruleMapper.updateById(rule);
    }

    /** 发货上下文。 */
    public static class DeliveryContext {
        public Long accountId, buyerId, productId;
        public Integer buyerCredit;
        public Double price;
        public String region;
        public LocalTime time;
        public DeliveryContext(Long accountId, Long buyerId, Long productId, Double price, String region) {
            this.accountId = accountId; this.buyerId = buyerId; this.productId = productId;
            this.price = price; this.region = region;
        }
    }

    /** 规则评估结果。 */
    public static class BlockDecision {
        public final boolean block;
        public final String action;    // BLOCK / NOTIFY_ONLY / DELAY
        public final String ruleName;
        public final String reason;
        public BlockDecision(boolean block, String action, String ruleName, String reason) {
            this.block = block; this.action = action; this.ruleName = ruleName; this.reason = reason;
        }
    }
}
