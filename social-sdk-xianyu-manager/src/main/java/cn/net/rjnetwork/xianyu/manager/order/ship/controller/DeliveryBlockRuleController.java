package cn.net.rjnetwork.xianyu.manager.order.ship.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.ship.mapper.DeliveryBlockRuleMapper;
import cn.net.rjnetwork.xianyu.manager.order.ship.model.DeliveryBlockRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 发货拦截规则管理 API —— A7。
 * <p>给前端「发货规则」页 CRUD。规则按 priority 升序评估，首个命中的决定动作。
 * ruleType 取 BUYER_BLACK/BUYER_LOW_CREDIT/PRODUCT_BLOCK/PRICE_RANGE/REGION_BLOCK/TIME_WINDOW。</p>
 */
@RestController
@RequestMapping("/api/delivery-block-rules")
public class DeliveryBlockRuleController {

    private final DeliveryBlockRuleMapper ruleMapper;

    public DeliveryBlockRuleController(DeliveryBlockRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /** 分页查询规则，可选 accountId/ruleType/enabled 过滤。 */
    @GetMapping
    public ApiResponse<Page<DeliveryBlockRule>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Integer enabled) {
        Page<DeliveryBlockRule> p = new Page<>(page, size);
        LambdaQueryWrapper<DeliveryBlockRule> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) wrapper.eq(DeliveryBlockRule::getAccountId, accountId);
        if (ruleType != null && !ruleType.isBlank()) wrapper.eq(DeliveryBlockRule::getRuleType, ruleType);
        if (enabled != null) wrapper.eq(DeliveryBlockRule::getEnabled, enabled);
        wrapper.orderByAsc(DeliveryBlockRule::getPriority);
        return ApiResponse.ok(ruleMapper.selectPage(p, wrapper));
    }

    /** 新建规则。 */
    @PostMapping
    public ApiResponse<DeliveryBlockRule> create(@RequestBody DeliveryBlockRule rule) {
        if (rule.getEnabled() == null) rule.setEnabled(1);
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getAction() == null) rule.setAction("BLOCK");
        if (rule.getHitCount() == null) rule.setHitCount(0);
        ruleMapper.insert(rule);
        return ApiResponse.ok(rule);
    }

    /** 更新规则。 */
    @PutMapping("/{id}")
    public ApiResponse<DeliveryBlockRule> update(@PathVariable Long id, @RequestBody DeliveryBlockRule rule) {
        rule.setId(id);
        ruleMapper.updateById(rule);
        return ApiResponse.ok(rule);
    }

    /** 启停规则。 */
    @PutMapping("/{id}/toggle")
    public ApiResponse<Boolean> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        DeliveryBlockRule rule = ruleMapper.selectById(id);
        if (rule == null) return ApiResponse.fail("NOT_FOUND", "规则不存在");
        rule.setEnabled(enabled ? 1 : 0);
        ruleMapper.updateById(rule);
        return ApiResponse.ok(true);
    }

    /** 删除规则（软删）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
