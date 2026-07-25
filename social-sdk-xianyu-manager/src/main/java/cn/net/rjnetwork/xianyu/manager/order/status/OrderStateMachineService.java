package cn.net.rjnetwork.xianyu.manager.order.status;

import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单状态机服务 —— BOT-O1。
 *
 * <p>封装订单状态迁移：</p>
 * <ol>
 *   <li>校验迁移合法性（{@link OrderStatusMachine#transition}）；</li>
 *   <li>进入退款状态时，保留 pre_refund_status 快照；退款取消/驳回时回滚；</li>
 *   <li>更新 xianyu_order.order_status（+ pre_refund_status）；</li>
 *   <li>记日志便于审计。</li>
 * </ol>
 */
@Service
public class OrderStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachineService.class);

    private final OrderMapper orderMapper;

    public OrderStateMachineService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 迁移订单状态机。
     *
     * @param orderId 闲鱼订单号（xianyu_order.order_id）
     * @param to      目标状态
     * @return 更新后的订单；订单不存在或迁移非法时抛异常
     */
    @Transactional
    public XianyuOrder transition(String orderId, String to) {
        XianyuOrder order = findByOrderId(orderId);
        String from = order.getOrderStatus() == null ? OrderStatusMachine.CREATED : order.getOrderStatus();
        if (from.equals(to)) {
            log.debug("[BOT-O1] 订单 {} 状态保持 {}（无迁移）", orderId, to);
            return order;
        }
        // 进入退款状态：保留 pre_refund_status 快照（若尚未保留）
        if (OrderStatusMachine.isRefundState(to) && order.getPreRefundStatus() == null) {
            order.setPreRefundStatus(from);
            log.info("[BOT-O1] 订单 {} 进入退款态 {}，保留快照 pre_refund_status={}", orderId, to, from);
        }
        // 退款取消/驳回（REFUNDING -> 非 REFUND_*）：回滚 pre_refund_status
        if (OrderStatusMachine.REFUNDING.equals(from) && !OrderStatusMachine.isRefundState(to)) {
            String rollback = order.getPreRefundStatus();
            if (rollback != null) {
                log.info("[BOT-O1] 订单 {} 退款取消/驳回，从 {} 回滚到 pre_refund_status={}（实际目标 to={}）",
                        orderId, from, rollback, to);
            }
            order.setPreRefundStatus(null);
        }
        OrderStatusMachine.transition(from, to);
        order.setOrderStatus(to);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("[BOT-O1] 订单 {} 状态机迁移：{} -> {}", orderId, from, to);
        return order;
    }

    /**
     * 直接设置订单状态机状态（不校验迁移合法性；用于订单同步初始化）。
     */
    @Transactional
    public XianyuOrder setStatus(String orderId, String status) {
        XianyuOrder order = findByOrderId(orderId);
        order.setOrderStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    private XianyuOrder findByOrderId(String orderId) {
        XianyuOrder order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getOrderId, orderId)
                        .last("LIMIT 1"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在 orderId=" + orderId);
        }
        return order;
    }
}
