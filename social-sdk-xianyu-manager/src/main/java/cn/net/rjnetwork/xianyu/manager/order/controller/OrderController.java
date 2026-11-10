package cn.net.rjnetwork.xianyu.manager.order.controller;

import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderService;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderSyncService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderSyncService orderSyncService;
    private final cn.net.rjnetwork.xianyu.manager.order.service.RefundMonitorService refundMonitorService;

    public OrderController(OrderService orderService, OrderSyncService orderSyncService,
                           cn.net.rjnetwork.xianyu.manager.order.service.RefundMonitorService refundMonitorService) {
        this.orderService = orderService;
        this.orderSyncService = orderSyncService;
        this.refundMonitorService = refundMonitorService;
    }

    /** 手动触发一轮退款分级监控（已发货→自动拒绝话术+通知；未发货→通知人工）。 */
    @PostMapping("/refund-monitor/run")
    @Audit("退款监控手动触发")
    public ApiResponse<Integer> runRefundMonitor() {
        return ApiResponse.ok(refundMonitorService.runMonitor());
    }

    @GetMapping
    public ApiResponse<Page<XianyuOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(orderService.listPage(page, size, accountId, type));
    }

    @PostMapping("/accounts/{accountId}/sync")
    public ApiResponse<OrderSyncService.SyncResult> sync(@PathVariable Long accountId) {
        OrderSyncService.SyncResult result = orderSyncService.syncOrders(accountId);
        if (!result.success) {
            return ApiResponse.fail("SYNC_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    /**
     * 调试：查看 API 原始返回（开发诊断用）
     */
    @GetMapping("/accounts/{accountId}/debug")
    public ApiResponse<java.util.Map<String, Object>> debug(@PathVariable Long accountId) {
        return ApiResponse.ok(orderSyncService.debugRawResponse(accountId));
    }

    @GetMapping("/{id}")
    public ApiResponse<XianyuOrder> getById(@PathVariable Long id) {
        XianyuOrder order = orderService.getById(id);
        if (order == null) return ApiResponse.fail("NOT_FOUND", "Order not found");
        return ApiResponse.ok(order);
    }

    /** 订单详情：聚合订单 + 商品 + 自动发货记录 + 物流，给前端详情抽屉用 */
    @GetMapping("/{id}/detail")
    public ApiResponse<Map<String, Object>> getOrderDetail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrderDetail(id));
    }

    @PostMapping("/{id}/delivery")
    @Audit("订单发货")
    public ApiResponse<XianyuOrder> delivery(@PathVariable Long id, @RequestParam String trackingNo) {
        return ApiResponse.ok(orderService.delivery(id, trackingNo));
    }
}
