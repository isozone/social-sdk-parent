package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 退款分级自动处理 —— 定时轮询退款中列表，按订单发货状态分级：
 * <ul>
 *   <li>已发货（虚拟已交付）：自动给买家发拒绝沟通话术（虚拟资源已交付不支持退款），
 *       并邮件通知卖家去闲鱼后台点「拒绝退款」。拒绝话术只是沟通，平台侧的拒绝操作仍需人工点击
 *       （SDK 无同意/拒绝退款的 MTOP 接口，需抓包后补充）。</li>
 *   <li>未发货：不发任何话术、不做动作，仅邮件通知人工处理（拒绝未发货订单会被平台判败）。</li>
 * </ul>
 * 已处理的退款单记入 refund_handle_log 防重复。
 */
@Service
public class RefundMonitorService {

    private static final Logger log = LoggerFactory.getLogger(RefundMonitorService.class);

    /** 已发货虚拟订单的自动拒绝沟通话术 */
    private static final String REFUSE_SCRIPT =
            "亲，非常理解您的心情～您购买的虚拟资源已在下单后自动发送（网盘链接可直接使用），"
            + "虚拟商品一经交付无法回收，按闲鱼规则不支持退款哦。使用中遇到任何问题（链接打不开、内容疑问）"
            + "随时联系我，一定给您解决到位～";

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    /** 多账号代理绑定：按账号出口 IP 隔离（未配置代理池时为 null，退化为直连） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProxyPoolManager proxyPoolManager;

    @Autowired
    public RefundMonitorService(OrderMapper orderMapper,
                                AccountMapper accountMapper, MessageService messageService,
                                ApplicationEventPublisher eventPublisher,
                                JdbcTemplate jdbcTemplate) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
        initTable();
    }

    /** 防重复处理记录表（启动时兜底建表，schema.sql 同步维护） */
    private void initTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS refund_handle_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "account_id INTEGER NOT NULL,"
                    + "order_id TEXT NOT NULL,"
                    + "refund_status TEXT,"
                    + "action TEXT NOT NULL,"
                    + "detail TEXT,"
                    + "created_at TEXT DEFAULT (datetime('now')),"
                    + "deleted INTEGER DEFAULT 0)");
        } catch (Exception e) {
            log.warn("[REFUND] init refund_handle_log failed: {}", e.getMessage());
        }
    }

    /**
     * 执行一轮退款监控。两条数据源：
     * ① MTOP 退款列表（disputeStatus 1/2/3=退款中，最及时，XianyuApiFacade 需按账号 cookie 构造）；
     * ② 本地订单库扫描 status=REFUNDING 的 SOLD 订单（兜底：接口返回结构变化/为空时不漏单，
     *    订单同步任务每 30 分钟已把退款状态落到本地）。
     * @return 本轮处理的退款单数
     */
    public int runMonitor() {
        int handled = 0;
        LambdaQueryWrapper<XianyuAccount> w = new LambdaQueryWrapper<>();
        w.eq(XianyuAccount::getStatus, "ACTIVE");
        for (XianyuAccount account : accountMapper.selectList(w)) {
            for (String status : new String[]{"1", "2", "3"}) {
                try {
                    XianyuApiFacade facade = new XianyuApiFacade(account.getCookieHeader(), proxyPoolManager, account.getId());
                    handled += processRefundPage(account.getId(), facade, status);
                } catch (Exception e) {
                    log.warn("[REFUND] account {} query refund list status={} failed: {}",
                            account.getId(), status, e.getMessage());
                }
            }
        }
        handled += scanLocalRefunding();
        return handled;
    }

    /** 兜底：扫描本地订单库中退款中（REFUNDING）的卖家 SOLD 订单，逐一分级处理。 */
    private int scanLocalRefunding() {
        int count = 0;
        try {
            LambdaQueryWrapper<XianyuOrder> w = new LambdaQueryWrapper<>();
            w.eq(XianyuOrder::getType, "SOLD")
                    .eq(XianyuOrder::getStatus, "REFUNDING")
                    .orderByDesc(XianyuOrder::getUpdatedAt)
                    .last("LIMIT 100");
            for (XianyuOrder order : orderMapper.selectList(w)) {
                try {
                    if (order.getAccountId() == null || order.getOrderId() == null) continue;
                    if (isHandled(order.getAccountId(), order.getOrderId())) continue;
                    markHandled(order.getAccountId(), order.getOrderId(), "LOCAL_SCAN");
                    handleRefund(order.getAccountId(), order.getOrderId(), order, null);
                    count++;
                } catch (Exception e) {
                    log.warn("[REFUND] local scan order {} failed: {}", order.getOrderId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[REFUND] local scan failed: {}", e.getMessage());
        }
        return count;
    }

    private int processRefundPage(Long accountId, XianyuApiFacade facade, String disputeStatus) {
        JsonNode resp = facade.getRefundList(disputeStatus, "1", "50");
        if (resp == null || !isMtopSuccess(resp)) {
            log.warn("[REFUND] refund list not success, disputeStatus={}, resp={}", disputeStatus, resp == null ? "null" : resp.toString());
            return 0;
        }
        JsonNode rows = resp.path("data").path("data");
        if (!rows.isArray() || rows.isEmpty()) {
            rows = resp.path("data").path("resultList");
        }
        if (!rows.isArray()) {
            // 首次接入：打印真实返回结构以便校准解析路径（稳定后可移除）
            log.info("[REFUND] refund list rows not array, disputeStatus={}, dataKeys={}, resp={}",
                    disputeStatus, resp.path("data").fieldNames(),
                    resp.toString().length() > 600 ? resp.toString().substring(0, 600) : resp.toString());
            return 0;
        }
        log.info("[REFUND] refund list disputeStatus={} rows={}", disputeStatus, rows.size());

        int count = 0;
        for (JsonNode row : rows) {
            try {
                String orderId = firstText(row, "orderId", "bizOrderId", "order_id", "id");
                if (orderId == null || orderId.isBlank()) continue;

                if (isHandled(accountId, orderId)) continue;
                markHandled(accountId, orderId, disputeStatus);

                XianyuOrder order = findOrder(accountId, orderId);
                handleRefund(accountId, orderId, order, row);
                count++;
            } catch (Exception e) {
                log.warn("[REFUND] process refund row failed: {}", e.getMessage());
            }
        }
        return count;
    }

    /** 分级：已发货虚拟订单 → 自动拒绝话术 + 通知点拒绝；未发货 → 通知人工 */
    private void handleRefund(Long accountId, String orderId, XianyuOrder order, JsonNode row) {
        XianyuAccount account = accountMapper.selectById(accountId);
        String accountName = account != null && account.getDisplayName() != null
                ? account.getDisplayName() : String.valueOf(accountId);

        if (order == null) {
            publishRefundEvent(accountId, accountName, orderId, "未知订单", null, "MANUAL",
                    "本地订单库未同步到该订单，请打开闲鱼 App 人工核实退款原因后处理。");
            return;
        }
        boolean delivered = order.getVirtualShippedAt() != null
                || "SHIPPED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus());

        if (delivered) {
            // 已发货（虚拟已交付）：自动发拒绝沟通话术，拒绝操作请人工点（平台规则如此最稳）
            String refuseDetail = "虚拟资源已交付，系统已自动向买家发送拒绝沟通话术。"
                    + "请到闲鱼 App「我的-退款/售后」对该订单点「拒绝退款」（SDK 暂无自动拒绝接口）。";
            try {
                MessageSendRequest req = new MessageSendRequest();
                req.setAccountId(accountId);
                req.setOrderId(orderId);
                req.setBuyerId(order.getBuyerId());
                req.setContent(REFUSE_SCRIPT);
                req.setAutoReply(true);
                messageService.sendMessage(req);
                refuseDetail += " 拒绝话术已送达买家。";
                log.info("[REFUND] order {} delivered -> refuse script sent to buyer", orderId);
            } catch (Exception e) {
                refuseDetail += " 注意：自动发送话术失败（" + e.getMessage() + "），请手动回复买家。";
                log.warn("[REFUND] refuse script send failed order {}: {}", orderId, e.getMessage());
            }
            publishRefundEvent(accountId, accountName, orderId, order.getItemTitle(), order.getAmount(),
                    "AUTO_REFUSE_SCRIPT", refuseDetail);
        } else {
            publishRefundEvent(accountId, accountName, orderId, order.getItemTitle(), order.getAmount(),
                    "MANUAL", "订单尚未发货，拒绝退款会被平台判败，请人工处理：同意退款或尽快发货后再拒。");
            log.info("[REFUND] order {} not delivered -> notify manual", orderId);
        }
    }

    private void publishRefundEvent(Long accountId, String accountName, String orderId, String itemTitle,
                                    java.math.BigDecimal amount, String action, String detail) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("accountName", accountName);
        vars.put("orderId", orderId);
        vars.put("itemTitle", itemTitle == null ? "未知商品" : itemTitle);
        vars.put("amount", amount == null ? "未知" : amount.toPlainString());
        vars.put("action", "AUTO_REFUSE_SCRIPT".equals(action)
                ? "【已自动发拒绝话术】请到闲鱼 App 点拒绝退款" : "【转人工】未发货订单不建议自动拒绝");
        vars.put("detail", detail);
        eventPublisher.publishEvent(new NotifyEvent("ORDER_REFUND_REQUESTED", accountId, accountName, vars));
    }

    private XianyuOrder findOrder(Long accountId, String orderId) {
        LambdaQueryWrapper<XianyuOrder> w = new LambdaQueryWrapper<>();
        w.eq(XianyuOrder::getAccountId, accountId)
                .eq(XianyuOrder::getOrderId, orderId)
                .orderByDesc(XianyuOrder::getUpdatedAt)
                .last("LIMIT 1");
        return orderMapper.selectOne(w);
    }

    private boolean isHandled(Long accountId, String orderId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_handle_log WHERE account_id=? AND order_id=? AND deleted=0",
                Integer.class, accountId, orderId);
        return n != null && n > 0;
    }

    private void markHandled(Long accountId, String orderId, String refundStatus) {
        jdbcTemplate.update("INSERT INTO refund_handle_log (account_id, order_id, refund_status, action, created_at, deleted) "
                        + "VALUES (?,?,?,?,datetime('now'),0)",
                accountId, orderId, refundStatus, "NOTIFIED");
    }

    private boolean isMtopSuccess(JsonNode resp) {
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && !ret.isEmpty()) {
            return ret.get(0).asText("").contains("SUCCESS");
        }
        return "SUCCESS".equalsIgnoreCase(resp.path("success").asText(""));
    }

    private String firstText(JsonNode row, String... fields) {
        for (String f : fields) {
            JsonNode v = row.path(f);
            if (!v.isMissingNode() && !v.isNull() && !v.asText("").isBlank()) return v.asText();
        }
        return null;
    }
}
