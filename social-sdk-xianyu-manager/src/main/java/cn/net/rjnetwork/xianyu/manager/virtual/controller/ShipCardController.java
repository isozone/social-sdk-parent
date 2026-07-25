package cn.net.rjnetwork.xianyu.manager.virtual.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卡券管理 API —— A6。
 * <p>给前端「卡券池」页 + 商品页「挂卡券」弹窗调用。支持四类型卡券 CRUD +
 * 商品-卡券关联（priority 排序，多卡券组合发货）。</p>
 */
@RestController
@RequestMapping("/api/ship-cards")
public class ShipCardController {

    private final ShipCardMapper shipCardMapper;
    private final CardItemRelationMapper relationMapper;

    public ShipCardController(ShipCardMapper shipCardMapper, CardItemRelationMapper relationMapper) {
        this.shipCardMapper = shipCardMapper;
        this.relationMapper = relationMapper;
    }

    /** 分页查询卡券池，可选 cardType/status 过滤。 */
    @GetMapping
    public ApiResponse<Page<ShipCard>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cardType,
            @RequestParam(required = false) String status) {
        Page<ShipCard> p = new Page<>(page, size);
        LambdaQueryWrapper<ShipCard> wrapper = new LambdaQueryWrapper<>();
        if (cardType != null && !cardType.isBlank()) wrapper.eq(ShipCard::getCardType, cardType);
        if (status != null && !status.isBlank()) wrapper.eq(ShipCard::getStatus, status);
        wrapper.orderByDesc(ShipCard::getCreatedAt);
        return ApiResponse.ok(shipCardMapper.selectPage(p, wrapper));
    }

    /** 新建卡券。 */
    @PostMapping
    public ApiResponse<ShipCard> create(@RequestBody ShipCard card) {
        if (card.getStatus() == null) card.setStatus("AVAILABLE");
        shipCardMapper.insert(card);
        return ApiResponse.ok(card);
    }

    /** 更新卡券。 */
    @PutMapping("/{id}")
    public ApiResponse<ShipCard> update(@PathVariable Long id, @RequestBody ShipCard card) {
        card.setId(id);
        shipCardMapper.updateById(card);
        return ApiResponse.ok(card);
    }

    /** 删除卡券（软删）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        shipCardMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    /** 查商品挂的所有启用卡券关联（按 priority 升序）。 */
    @GetMapping("/relations/{productId}")
    public ApiResponse<List<CardItemRelation>> listRelations(@PathVariable Long productId) {
        return ApiResponse.ok(relationMapper.selectEnabledByProductId(productId));
    }

    /** 给商品挂一张卡券（priority/enabled 可空，默认 0/1）。 */
    @PostMapping("/relations")
    public ApiResponse<CardItemRelation> addRelation(@RequestBody CardItemRelation relation) {
        if (relation.getPriority() == null) relation.setPriority(0);
        if (relation.getEnabled() == null) relation.setEnabled(1);
        relationMapper.insert(relation);
        return ApiResponse.ok(relation);
    }

    /** 启停 / 改优先级商品-卡券关联。 */
    @PutMapping("/relations/{id}")
    public ApiResponse<CardItemRelation> updateRelation(
            @PathVariable Long id,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) Integer enabled) {
        CardItemRelation rel = relationMapper.selectById(id);
        if (rel == null) return ApiResponse.fail("NOT_FOUND", "关联不存在");
        if (priority != null) rel.setPriority(priority);
        if (enabled != null) rel.setEnabled(enabled);
        relationMapper.updateById(rel);
        return ApiResponse.ok(rel);
    }

    /** 删除商品-卡券关联（软删）。 */
    @DeleteMapping("/relations/{id}")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        relationMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
