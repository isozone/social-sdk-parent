package cn.net.rjnetwork.xianyu.manager.order.ship.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.ship.model.DeliveryRule;
import cn.net.rjnetwork.xianyu.manager.order.ship.service.DeliveryMatchRuleService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 发货匹配规则管理端 API —— BOT-O3。
 * <p>不同于 {@link DeliveryBlockRuleController}（A7 拦截阻断），
 * 本接口是 <b>匹配</b> 发货：买家消息/订单关键词 → 命中卡券，决定发哪张卡、发几次。</p>
 */
@RestController
@RequestMapping("/api/delivery-rules")
public class DeliveryMatchRuleController {

    private final DeliveryMatchRuleService service;

    public DeliveryMatchRuleController(DeliveryMatchRuleService service) {
        this.service = service;
    }

    /** 分页查询发货匹配规则。 */
    @GetMapping
    public ApiResponse<Page<DeliveryRule>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String itemId) {
        return ApiResponse.ok(service.page(page, size, accountId, itemId));
    }

    /** 列表查询（不分页）。 */
    @GetMapping("/list")
    public ApiResponse<java.util.List<DeliveryRule>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String itemId) {
        return ApiResponse.ok(service.list(accountId, itemId));
    }

    /** 新建发货匹配规则。 */
    @PostMapping
    public ApiResponse<DeliveryRule> create(@RequestBody DeliveryRule rule) {
        return ApiResponse.ok(service.create(rule));
    }

    /** 更新发货匹配规则。 */
    @PutMapping("/{id}")
    public ApiResponse<DeliveryRule> update(@PathVariable Long id, @RequestBody DeliveryRule patch) {
        return ApiResponse.ok(service.update(id, patch));
    }

    /** 删除发货匹配规则。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 匹配发货规则（内部/调试用）：传入账号+商品+文本，返回命中的规则。
     * 生产链路里由 AutoShipService 直接调 DeliveryMatchRuleService.match。
     */
    @PostMapping("/match")
    public ApiResponse<DeliveryRule> match(@RequestParam Long accountId,
                                            @RequestParam(required = false) String itemId,
                                            @RequestParam String text) {
        return ApiResponse.ok(service.match(accountId, itemId, text));
    }
}
