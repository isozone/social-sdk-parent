package cn.net.rjnetwork.xianyu.manager.product.reply.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.reply.model.ItemReply;
import cn.net.rjnetwork.xianyu.manager.product.reply.service.ItemReplyService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品专属回复管理端 API —— BOT-D1。
 * <p>给前端「商品专属回复」页调用：CRUD + 启用/停用 + 预览渲染。
 * 不同于通用关键词回复（A10），本接口是商品级专属回复。</p>
 */
@RestController
@RequestMapping("/api/item-reply")
public class ItemReplyController {

    private final ItemReplyService service;

    public ItemReplyController(ItemReplyService service) {
        this.service = service;
    }

    /** 分页查询商品专属回复。 */
    @GetMapping
    public ApiResponse<Page<ItemReply>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) String triggerScene) {
        return ApiResponse.ok(service.page(page, size, accountId, itemId, triggerScene));
    }

    /** 列表查询（不分页）。 */
    @GetMapping("/list")
    public ApiResponse<List<ItemReply>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) String triggerScene) {
        return ApiResponse.ok(service.list(accountId, itemId, triggerScene));
    }

    /** 新建商品专属回复。 */
    @PostMapping
    public ApiResponse<ItemReply> create(@RequestBody ItemReply reply) {
        return ApiResponse.ok(service.create(reply));
    }

    /** 更新商品专属回复。 */
    @PutMapping("/{id}")
    public ApiResponse<ItemReply> update(@PathVariable Long id, @RequestBody ItemReply patch) {
        return ApiResponse.ok(service.update(id, patch));
    }

    /** 删除商品专属回复。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /** 启用/停用（账号级开关）。 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<Void> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggleEnabled(id, enabled);
        return ApiResponse.ok(null);
    }

    /** 预览渲染：传入商品+场景+买家，返回随机选中的渲染后文本（写 useCount）。 */
    @PostMapping("/preview")
    public ApiResponse<String> preview(@RequestParam Long accountId,
                                       @RequestParam String itemId,
                                       @RequestParam(defaultValue = "FIRST_INQUIRY") String triggerScene,
                                       @RequestParam(required = false) String buyerNick) {
        return ApiResponse.ok(service.pickAndRender(accountId, itemId, triggerScene, buyerNick));
    }
}
