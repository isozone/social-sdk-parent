package cn.net.rjnetwork.xianyu.manager.order.ship.service;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.ship.mapper.DeliveryRuleMapper;
import cn.net.rjnetwork.xianyu.manager.order.ship.model.DeliveryRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 发货匹配规则服务 —— BOT-O3。
 * <p>不同于 {@link DeliveryRuleEngine}（A7 拦截阻断），本服务是 <b>匹配</b> 发货：
 * 买家消息/订单关键词 → 命中卡券（cardId），决定发哪张卡、发几次。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>CRUD delivery_rules 表（按账号/商品/关键词维度配置）；</li>
 *   <li>匹配时按 priority 升序、enabled=1 过滤，命中第一条即返回；</li>
 *   <li>命中后写 hitCount++/lastHitAt，供管理端统计。</li>
 * </ol>
 */
@Service
public class DeliveryMatchRuleService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMatchRuleService.class);

    private final DeliveryRuleMapper ruleMapper;

    public DeliveryMatchRuleService(DeliveryRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /** 分页查询发货匹配规则（可按 accountId/itemId 过滤）。 */
    public Page<DeliveryRule> page(long page, long size, Long accountId, String itemId) {
        Page<DeliveryRule> p = new Page<>(page, size);
        LambdaQueryWrapper<DeliveryRule> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(DeliveryRule::getAccountId, accountId);
        if (itemId != null && !itemId.isBlank()) w.eq(DeliveryRule::getItemId, itemId);
        w.orderByAsc(DeliveryRule::getPriority).orderByDesc(DeliveryRule::getCreatedAt);
        return ruleMapper.selectPage(p, w);
    }

    /** 列表查询（不分页，给前端下拉/匹配用）。 */
    public List<DeliveryRule> list(Long accountId, String itemId) {
        LambdaQueryWrapper<DeliveryRule> w = new LambdaQueryWrapper<>();
        if (accountId != null) w.eq(DeliveryRule::getAccountId, accountId);
        if (itemId != null && !itemId.isBlank()) w.eq(DeliveryRule::getItemId, itemId);
        w.orderByAsc(DeliveryRule::getPriority).orderByDesc(DeliveryRule::getCreatedAt);
        return ruleMapper.selectList(w);
    }

    @Transactional
    public DeliveryRule create(DeliveryRule rule) {
        if (rule.getMatchMode() == null || rule.getMatchMode().isBlank()) rule.setMatchMode("CONTAINS");
        if (rule.getDeliveryCount() == null || rule.getDeliveryCount() <= 0) rule.setDeliveryCount(1);
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        if (rule.getHitCount() == null) rule.setHitCount(0);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    @Transactional
    public DeliveryRule update(Long id, DeliveryRule patch) {
        DeliveryRule existing = ruleMapper.selectById(id);
        if (existing == null) throw new IllegalArgumentException("规则不存在 id=" + id);
        if (patch.getAccountId() != null) existing.setAccountId(patch.getAccountId());
        if (patch.getItemId() != null) existing.setItemId(patch.getItemId());
        if (patch.getKeyword() != null) existing.setKeyword(patch.getKeyword());
        if (patch.getMatchMode() != null) existing.setMatchMode(patch.getMatchMode());
        if (patch.getCardId() != null) existing.setCardId(patch.getCardId());
        if (patch.getDeliveryCount() != null) existing.setDeliveryCount(patch.getDeliveryCount());
        if (patch.getPriority() != null) existing.setPriority(patch.getPriority());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());
        if (patch.getRemark() != null) existing.setRemark(patch.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    /**
     * 匹配发货规则：按 priority 升序、enabled=1 过滤，命中第一条即返回。
     * 支持三种 matchMode：CONTAINS（包含任一关键词）/EXACT（完全相等）/REGEX（正则）。
     *
     * @param accountId 账号 ID
     * @param itemId    商品 ID（可空）
     * @param text      待匹配文本（买家消息/订单标题等）
     * @return 命中的规则；无命中返回 null
     */
    public DeliveryRule match(Long accountId, String itemId, String text) {
        if (text == null || text.isBlank()) return null;
        LambdaQueryWrapper<DeliveryRule> w = new LambdaQueryWrapper<>();
        w.eq(DeliveryRule::getEnabled, 1);
        // 账号维度：先匹配 accountId 指定的，再匹配 accountId=null 的全局规则
        w.and(ww -> ww.eq(DeliveryRule::getAccountId, accountId).or().isNull(DeliveryRule::getAccountId));
        if (itemId != null && !itemId.isBlank()) {
            w.and(ww -> ww.eq(DeliveryRule::getItemId, itemId).or().isNull(DeliveryRule::getItemId));
        }
        w.orderByAsc(DeliveryRule::getPriority);
        List<DeliveryRule> candidates = ruleMapper.selectList(w);
        // 按优先级排序：accountId 精确匹配优先 > itemId 精确匹配优先 > priority 数值
        candidates.sort(Comparator
                .comparingInt((DeliveryRule r) -> r.getAccountId() != null && r.getAccountId().equals(accountId) ? 0 : 1)
                .thenComparingInt((DeliveryRule r) -> r.getItemId() != null && r.getItemId().equals(itemId) ? 0 : 1)
                .thenComparingInt(r -> Optional.ofNullable(r.getPriority()).orElse(100)));
        for (DeliveryRule rule : candidates) {
            if (matches(rule, text)) {
                // 命中后写 hitCount++/lastHitAt
                rule.setHitCount(Optional.ofNullable(rule.getHitCount()).orElse(0) + 1);
                rule.setLastHitAt(LocalDateTime.now());
                try { ruleMapper.updateById(rule); } catch (Exception e) {
                    log.warn("[BOT-O3] 规则 id={} 命中统计更新失败（非致命）: {}", rule.getId(), e.getMessage());
                }
                return rule;
            }
        }
        return null;
    }

    private boolean matches(DeliveryRule rule, String text) {
        String keyword = rule.getKeyword();
        if (keyword == null || keyword.isBlank()) return false;
        String mode = rule.getMatchMode() == null ? "CONTAINS" : rule.getMatchMode().toUpperCase();
        switch (mode) {
            case "EXACT":
                return text.trim().equals(keyword.trim());
            case "REGEX":
                try { return text.matches(keyword); } catch (Exception e) { return false; }
            case "CONTAINS":
            default:
                // 多个关键词用英文逗号分隔，命中任一即匹配
                for (String kw : keyword.split(",")) {
                    String t = kw.trim();
                    if (!t.isEmpty() && text.contains(t)) return true;
                }
                return false;
        }
    }
}
