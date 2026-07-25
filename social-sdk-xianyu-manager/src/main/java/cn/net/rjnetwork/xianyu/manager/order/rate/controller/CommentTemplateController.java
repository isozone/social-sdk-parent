package cn.net.rjnetwork.xianyu.manager.order.rate.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.CommentTemplate;
import cn.net.rjnetwork.xianyu.manager.order.rate.service.CommentTemplateService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评价模板管理端 API —— BOT-B1。
 * <p>给前端「评价模板」页调用：CRUD + 启用/停用 + 预览渲染。</p>
 */
@RestController
@RequestMapping("/api/comment-templates")
public class CommentTemplateController {

    private final CommentTemplateService service;

    public CommentTemplateController(CommentTemplateService service) {
        this.service = service;
    }

    /** 分页查询评价模板。 */
    @GetMapping
    public ApiResponse<Page<CommentTemplate>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(service.page(page, size, accountId, category));
    }

    /** 列表查询（不分页）。 */
    @GetMapping("/list")
    public ApiResponse<List<CommentTemplate>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(service.list(accountId, category));
    }

    /** 新建评价模板。 */
    @PostMapping
    public ApiResponse<CommentTemplate> create(@RequestBody CommentTemplate tpl) {
        return ApiResponse.ok(service.create(tpl));
    }

    /** 更新评价模板。 */
    @PutMapping("/{id}")
    public ApiResponse<CommentTemplate> update(@PathVariable Long id, @RequestBody CommentTemplate patch) {
        return ApiResponse.ok(service.update(id, patch));
    }

    /** 删除评价模板。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /** 启用/停用模板（账号级开关）。 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<Void> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggleEnabled(id, enabled);
        return ApiResponse.ok(null);
    }

    /** 预览渲染：传入商品名+买家昵称，返回随机选中的模板渲染后文本（不写 useCount）。 */
    @PostMapping("/preview")
    public ApiResponse<String> preview(@RequestParam(required = false) Long accountId,
                                       @RequestParam(defaultValue = "POSITIVE") String category,
                                       @RequestParam(required = false) String productName,
                                       @RequestParam(required = false) String buyerNick) {
        return ApiResponse.ok(service.pickAndRender(accountId, category, productName, buyerNick));
    }
}
