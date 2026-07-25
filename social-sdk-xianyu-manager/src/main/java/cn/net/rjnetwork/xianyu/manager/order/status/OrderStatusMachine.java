package cn.net.rjnetwork.xianyu.manager.order.status;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机常量 —— BOT-O1。
 *
 * <p>状态枚举：</p>
 * <ul>
 *   <li>{@link #CREATED} — 下单已创建（待付款）</li>
 *   <li>{@link #PAID} — 已付款（待发货）</li>
 *   <li>{@link #SHIPPED} — 已发货（待买家确认/虚拟发货已发卡）</li>
 *   <li>{@link #DELIVERED} — 已送达（买家确认收货/自动确认）</li>
 *   <li>{@link #COMPLETED} — 已完成（评价/超时关闭）</li>
 *   <li>{@link #REFUNDING} — 退款中（保留 pre_refund_status 快照）</li>
 *   <li>{@link #REFUNDED} — 已退款</li>
 *   <li>{@link #CLOSED} — 已关闭（退款驳回/超时取消）</li>
 * </ul>
 *
 * <p>迁移表见 {@link #TRANSITIONS}；非法迁移抛 {@link IllegalStateException}。</p>
 */
public final class OrderStatusMachine {

    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String COMPLETED = "COMPLETED";
    public static final String REFUNDING = "REFUNDING";
    public static final String REFUNDED = "REFUNDED";
    public static final String CLOSED = "CLOSED";

    /** 合法迁移表：from -> {to...} */
    public static final Map<String, Set<String>> TRANSITIONS;

    /** 退款相关状态：进入时需保留 pre_refund_status 快照 */
    public static final Set<String> REFUND_STATES;

    static {
        Map<String, Set<String>> t = new HashMap<>();
        t.put(CREATED, Set.of(PAID, CLOSED, REFUNDING));
        t.put(PAID, Set.of(SHIPPED, REFUNDING, CLOSED));
        t.put(SHIPPED, Set.of(DELIVERED, REFUNDING, COMPLETED));
        t.put(DELIVERED, Set.of(COMPLETED, REFUNDING));
        t.put(COMPLETED, Set.of(REFUNDING));
        t.put(REFUNDING, Set.of(REFUNDED, COMPLETED, CLOSED, PAID, SHIPPED, DELIVERED));
        t.put(REFUNDED, Set.of());
        t.put(CLOSED, Set.of());
        TRANSITIONS = Collections.unmodifiableMap(t);
        REFUND_STATES = Set.of(REFUNDING, REFUNDED);
    }

    private OrderStatusMachine() {}

    /**
     * 校验迁移是否合法；不合法抛 IllegalStateException。
     * @return 目标状态 to
     */
    public static String transition(String from, String to) {
        if (to == null) throw new IllegalStateException("目标状态不能为空");
        if (from == null) from = CREATED;
        if (from.equals(to)) return to;
        Set<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException("非法订单状态迁移: " + from + " -> " + to);
        }
        return to;
    }

    /** 是否退款相关状态（需保留 pre_refund_status 快照） */
    public static boolean isRefundState(String status) {
        return REFUNDING.equals(status) || REFUNDED.equals(status);
    }

    /** 是否终态 */
    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || REFUNDED.equals(status) || CLOSED.equals(status);
    }
}
