package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuOrderApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.status.OrderStateMachineService;
import cn.net.rjnetwork.xianyu.manager.order.status.OrderStatusMachine;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.service.VirtualShipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单同步服务 - 按账号从闲鱼 API 拉取订单数据并同步到本地 DB
 */
@Service
public class OrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(OrderSyncService.class);

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /** 最大翻页次数，防止死循环（API 每次默认 10 条） */
    private static final int MAX_PAGES = 50;

    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final VirtualShipService virtualShipService;
    private final ApplicationEventPublisher eventPublisher;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;
    /** 订单状态机服务（BOT-O1）—— 可选注入避免循环依赖。 */
    private OrderStateMachineService orderStateMachineService;

    public OrderSyncService(AccountMapper accountMapper, OrderMapper orderMapper,
                            ProductMapper productMapper,
                            VirtualShipService virtualShipService,
                            ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.virtualShipService = virtualShipService;
        this.eventPublisher = eventPublisher;
    }

    /** Spring 注入 OrderStateMachineService（可选，避免启动期循环依赖）。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOrderStateMachineService(OrderStateMachineService orderStateMachineService) {
        this.orderStateMachineService = orderStateMachineService;
    }

    /**
     * 同步指定账号的订单（bought + sold），自动翻页直到没有更多数据
     */
    @Transactional
    public SyncResult syncOrders(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return SyncResult.error("账号不存在");
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            return SyncResult.error("账号未设置 Cookie");
        }

        try {
            XianyuMtopApiClient mtopClient = xianyuMtopClientFactory.create(account);
            XianyuOrderApiService orderApi = new XianyuOrderApiService(mtopClient);

            SyncResult result = new SyncResult();

            // 同步我买到的 (bought) — 数据结构: data.items[], data.nextPage, data.lastEndRow
            List<XianyuOrder> boughtOrders = syncBoughtOrders(orderApi, "BOUGHT", accountId);
            upsertOrders(boughtOrders, accountId, "BOUGHT");
            result.boughtCount = boughtOrders.size();

            // 同步我卖出的 (sold) — 数据结构: data.module.items[], module.nextPage, module.lastEndRow
            List<XianyuOrder> soldOrders = syncSoldOrders(orderApi, "SOLD", accountId);
            upsertOrders(soldOrders, accountId, "SOLD");
            result.soldCount = soldOrders.size();

            result.success = true;
            result.totalCount = result.boughtCount + result.soldCount;
            result.syncedAt = LocalDateTime.now();
            return result;

        } catch (Exception e) {
            return SyncResult.error("同步订单失败: " + e.getMessage());
        }
    }

    /**
     * 调试接口：返回 bought/sold API 原始返回的关键信息（解构结构 Helper）
     */
    public Map<String, Object> debugRawResponse(Long accountId) {
        Map<String, Object> debug = new HashMap<>();
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            debug.put("error", "账号不存在");
            return debug;
        }

        try {
            XianyuMtopApiClient mtopClient = xianyuMtopClientFactory.create(account);
            XianyuOrderApiService orderApi = new XianyuOrderApiService(mtopClient);

            // bought 原始结构（第一页）
            JsonNode boughtData = orderApi.getOrderList("1", "10");
            debug.put("bought_raw", boughtData);
            debug.put("bought_page", "1/10");
            debug.put("bought_nextPage", boughtData != null && boughtData.has("data") ? 
                      boughtData.get("data").path("nextPage").asBoolean(false) : false);
            debug.put("bought_totalCount", boughtData != null && boughtData.has("data") ? 
                      boughtData.get("data").path("totalCount").asInt(0) : 0);

            // sold 原始结构（第一页）
            JsonNode soldData = orderApi.getSoldOrderList("1", "10");
            debug.put("sold_raw", soldData);
            debug.put("sold_page", "1/10");
            debug.put("sold_nextPage", soldData != null && soldData.has("data") ? 
                      soldData.get("data").path("module").path("nextPage").asBoolean(false) : false);
            debug.put("sold_totalCount", soldData != null && soldData.has("data") ? 
                      soldData.get("data").path("module").path("totalCount").asInt(0) : 0);

        } catch (Exception e) {
            debug.put("exception", e.getMessage());
        }
        return debug;
    }

    // ===== Bought 订单同步（cursor-based翻页） =====

    /**
     * 同步 bought 订单，自动翻页。
     * API: mtop.idle.web.trade.bought.list
     * 结构: data.items[], data.lastEndRow, data.nextPage
     * 分页参数: 使用 lastEndRow（游标），pageNumber/pageSize 可能不生效
     */
    private List<XianyuOrder> syncBoughtOrders(XianyuOrderApiService orderApi, String type, Long accountId) {
        List<XianyuOrder> allOrders = new ArrayList<>();
        int lastEndRow = 0;
        int consecutiveFullPages = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = orderApi.getOrderList(String.valueOf(page), "20");
            if (response == null || !response.has("data")) break;

            JsonNode data = response.get("data");
            JsonNode items = data.path("items");
            if (!items.isArray()) break;

            // 检查是否有下一页
            boolean hasNext = data.path("nextPage").asBoolean(false);
            int newLastEndRow = data.path("lastEndRow").asInt(0);

            for (JsonNode item : items) {
                XianyuOrder order = parseBoughtItem(item, accountId, type);
                if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
                    allOrders.add(order);
                }
            }

            int count = items.size();
            if (count == 0) break;
            
            // 如果连续两页都返回相同数量的满页，认为API不再支持分页
            if (count >= 10 && consecutiveFullPages >= 2) break;
            if (count >= 10) consecutiveFullPages++;
            else consecutiveFullPages = 0;
            
            // 如果nextPage=false或items不足10个，没有更多数据
            if (!hasNext || count < 10) break;
            
            lastEndRow = newLastEndRow;
        }

        return allOrders;
    }

    /**
     * 同步 sold 订单，自动翻页。
     * API: mtop.taobao.idle.trade.merchant.sold.get
     * 结构: data.module.items[], data.module.lastEndRow, data.module.nextPage
     */
    private List<XianyuOrder> syncSoldOrders(XianyuOrderApiService orderApi, String type, Long accountId) {
        List<XianyuOrder> allOrders = new ArrayList<>();
        int consecutiveFullPages = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = orderApi.getSoldOrderList(String.valueOf(page), "20");
            if (response == null || !response.has("data")) break;

            JsonNode data = response.get("data");
            JsonNode module = data.path("module");
            if (!module.isObject()) break;

            JsonNode items = module.path("items");
            if (!items.isArray()) break;

            boolean hasNext = module.path("nextPage").asBoolean(false);
            int lastEndRow = module.path("lastEndRow").asInt(0);
            int totalCount = module.path("totalCount").asInt(0);

            for (JsonNode item : items) {
                XianyuOrder order = parseSoldItem(item, accountId, type);
                if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
                    allOrders.add(order);
                }
            }

            int count = items.size();
            if (count == 0) break;
            
            // 如果总数为0且没有下一页，结束
            if (totalCount == 0 && !hasNext) break;
            
            // 如果连续两页都返回相同数量的满页，认为API不再支持分页
            if (count >= 10 && consecutiveFullPages >= 2) break;
            if (count >= 10) consecutiveFullPages++;
            else consecutiveFullPages = 0;
            
            if (!hasNext || count < 10) break;
        }

        return allOrders;
    }

    // ===== 订单解析 =====

    /**
     * 解析 bought 订单项
     */
    private XianyuOrder parseBoughtItem(JsonNode item, Long accountId, String type) {
        XianyuOrder order = new XianyuOrder();
        order.setAccountId(accountId);
        order.setType(type);

        order.setRawData(item.toString());

        // commonData
        JsonNode commonData = item.path("commonData");
        if (commonData.isObject()) {
            // 优先用 orderIdStr 或 orderId
            order.setOrderId(commonData.has("orderIdStr") ? commonData.path("orderIdStr").asText() : getText(commonData, "orderId"));
            order.setItemId(getText(commonData, "itemId"));
            order.setTradeStatusEnum(getText(commonData, "tradeStatusEnum"));
            order.setOrderDetailUrl(getText(commonData, "orderDetailUrl"));
            String peerUserId = getText(commonData, "peerUserId");
            if ("SOLD".equals(type)) {
                order.setBuyerId(peerUserId);
                order.setIsSeller(true);
            } else {
                order.setSellerId(peerUserId);
                order.setIsSeller(Boolean.TRUE.equals(commonData.path("seller").asText(null)));
            }
        }

        // content.data.detailInfo / priceInfo
        JsonNode contentData = item.path("content").path("data");
        if (contentData.isObject()) {
            JsonNode detailInfo = contentData.path("detailInfo");
            if (detailInfo.isObject()) {
                order.setItemTitle(getText(detailInfo, "auctionTitle"));
            }
            JsonNode priceInfo = contentData.path("priceInfo");
            if (priceInfo.isObject()) {
                String priceStr = getText(priceInfo, "price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    try {
                        order.setAmount(new BigDecimal(priceStr));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // head.data.userInfo + statusViewMsg + createTime
        JsonNode headData = item.path("head").path("data");
        if (headData.isObject()) {
            JsonNode userInfo = headData.path("userInfo");
            if (userInfo.isObject()) {
                order.setCounterpartyName(getText(userInfo, "userNick"));
            }

            // 优先用 tradeStatusEnum 映射状态
            String tradeStatus = mapStatusFromEnum(getTradeStatusFromCommon(item, "commonData"));
            order.setStatus(tradeStatus != null ? tradeStatus : mapStatusFromMsg(getText(headData, "statusViewMsg")));

            String createTime = getText(headData, "createTime");
            if (createTime != null && !createTime.isEmpty()) {
                order.setOrderTime(parseDateTime(createTime));
            }
        }

        return order;
    }

    /**
     * 解析 sold 订单项 — sold 列表接口（mtop.taobao.idle.trade.merchant.sold.get）结构与 bought 完全不同：
     * <ul>
     *   <li>状态在 commonData.orderStatus（中文文本，如"待发货"/"已付款"），无 tradeStatusEnum；</li>
     *   <li>标题在 itemVO.title，金额在 priceVO.totalPrice，买家在 buyerInfoVO；</li>
     *   <li>时间在 commonData.createTime / paySuccessTime。</li>
     * </ul>
     */
    private XianyuOrder parseSoldItem(JsonNode item, Long accountId, String type) {
        XianyuOrder order = new XianyuOrder();
        order.setAccountId(accountId);
        order.setType(type);
        order.setRawData(item.toString());
        order.setIsSeller(true);

        // commonData：orderId / itemId / orderStatus（中文）/ createTime
        JsonNode commonData = item.path("commonData");
        if (commonData.isObject()) {
            order.setOrderId(commonData.has("orderIdStr") ? commonData.path("orderIdStr").asText() : getText(commonData, "orderId"));
            order.setItemId(getText(commonData, "itemId"));
            order.setTradeStatusEnum(getText(commonData, "tradeStatusEnum"));
            order.setOrderDetailUrl(getText(commonData, "orderDetailUrl"));

            // 状态：优先 tradeStatusEnum，兜底中文 orderStatus（"待发货"=PAID，"待付款"=PENDING 等）
            String tradeStatus = mapStatusFromEnum(getText(commonData, "tradeStatusEnum"));
            order.setStatus(tradeStatus != null ? tradeStatus : mapStatusFromMsg(getText(commonData, "orderStatus")));

            String createTime = getText(commonData, "createTime");
            if (createTime != null && !createTime.isEmpty()) {
                order.setOrderTime(parseDateTime(createTime));
            }
        }

        // itemVO.title：商品标题
        JsonNode itemVO = item.path("itemVO");
        if (itemVO.isObject()) {
            order.setItemTitle(getText(itemVO, "title"));
        }

        // priceVO.totalPrice：订单金额（兜底 auctionPrice）
        JsonNode priceVO = item.path("priceVO");
        if (priceVO.isObject()) {
            String priceStr = getText(priceVO, "totalPrice");
            if (priceStr == null || priceStr.isEmpty()) priceStr = getText(priceVO, "auctionPrice");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    order.setAmount(new BigDecimal(priceStr));
                } catch (NumberFormatException ignored) {}
            }
        }

        // buyerInfoVO：买家昵称 + buyerId
        JsonNode buyerInfo = item.path("buyerInfoVO");
        if (buyerInfo.isObject()) {
            order.setCounterpartyName(getText(buyerInfo, "userNick"));
            String buyerId = getText(buyerInfo, "buyerId");
            if (buyerId != null && !buyerId.isEmpty()) {
                order.setBuyerId(buyerId);
            }
        }

        return order;
    }

    /**
     * 尝试从 commonData 中提取 tradeStatusEnum
     */
    private String getTradeStatusFromCommon(JsonNode item, String field) {
        JsonNode common = item.path(field);
        if (common.isObject()) {
            return getText(common, "tradeStatusEnum");
        }
        return null;
    }

    /**
     * 将 tradeStatusEnum 映射为标准状态码
     * 真实值来自 API: refund_success, buyer_to_confirm, trade_success 等
     */
    private String mapStatusFromEnum(String enumVal) {
        if (enumVal == null || enumVal.isEmpty()) return null; // 无枚举值 → 让调用方走 statusViewMsg/orderStatus 兜底

        switch (enumVal) {
            case "trade_success": return "COMPLETED";
            case "buyer_to_confirm": return "PAID"; // 买家待确认收货
            case "refund_success":
            case "refund_refund":
            case "trade_refund": return "REFUNDED";
            case "trade_in_audit":
            case "refund_agree":
            case "refund_process": return "REFUNDING";
            case "trade_closed":
            case "trade_cancelled":
            case "cancel": return "CLOSED";
            case "pending_pay":
            case "waiting_pay":
            case "trade_pending": return "PENDING";
            case "trade_delivered":
            case "sent": return "SHIPPED";
            case "paid":
            case "trade_paid": return "PAID";
            case "trade_suspended": return "PENDING";
            default:
                // 未知枚举，fallback 到状态消息
                return null;
        }
    }

    /**
     * 将 API 状态消息映射到标准状态码（兜底方案）
     */
    private String mapStatusFromMsg(String statusMsg) {
        if (statusMsg == null) return "PENDING";
        if (statusMsg.contains("待付款")) return "PENDING";
        if (statusMsg.contains("待发货") || statusMsg.contains("已付款")) return "PAID";
        if (statusMsg.contains("已发货") || statusMsg.contains("等待见面交易")) return "SHIPPED";
        if (statusMsg.contains("已完成") || statusMsg.contains("交易成功") || statusMsg.contains("交易完成")) return "COMPLETED";
        if (statusMsg.contains("退款中") || statusMsg.contains("协商退款")) return "REFUNDING";
        if (statusMsg.contains("退款成功") || statusMsg.contains("有退款") || statusMsg.contains("已退款")) return "REFUNDED";
        if (statusMsg.contains("已关闭") || statusMsg.contains("交易关闭")) return "CLOSED";
        return "PENDING";
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(dateStr, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 批量 upsert 订单（按 accountId + orderId 去重）
     */
    private void upsertOrders(List<XianyuOrder> orders, Long accountId, String type) {
        String accountName = accountName(accountId);
        for (XianyuOrder order : orders) {
            if (order.getOrderId() == null || order.getOrderId().isEmpty()) continue;

            XianyuOrder existing = findByAccountIdAndOrderId(accountId, order.getOrderId());
            if (existing != null) {
                boolean statusChanged = !str(existing.getStatus()).equals(str(order.getStatus()));
                boolean titleChanged = !str(existing.getItemTitle()).equals(str(order.getItemTitle()));

                existing.setItemId(order.getItemId());
                existing.setItemTitle(order.getItemTitle());
                existing.setCounterpartyName(order.getCounterpartyName());
                existing.setBuyerId(order.getBuyerId());
                existing.setSellerId(order.getSellerId());
                existing.setOrderDetailUrl(order.getOrderDetailUrl());
                existing.setRawData(order.getRawData());
                existing.setAmount(order.getAmount());
                existing.setStatus(order.getStatus());
                existing.setOrderTime(order.getOrderTime());
                existing.setTradeStatusEnum(order.getTradeStatusEnum());
                existing.setIsSeller(order.getIsSeller());
                resolveProductRef(existing, accountId);
                existing.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateById(existing);
                // BOT-O1 订单状态机迁移：闲鱼原始 status 映射到 order_status 状态机
                if (statusChanged) {
                    transitionOrderStatus(existing.getOrderId(), mapToOrderStatus(order.getStatus()));
                }
                tryCreateVirtualShipTask(existing);

                if (statusChanged || titleChanged) {
                    eventPublisher.publishEvent(new NotifyEvent(
                            statusChanged ? "ORDER_STATUS_CHANGED" : "ORDER_UPDATED",
                            accountId, accountName,
                            Map.of("accountName", accountName, "orderId", order.getOrderId(),
                                    "itemTitle", str(order.getItemTitle()), "status", str(order.getStatus()))));
                }
            } else {
                // 新订单：按 itemTitle + accountId 反查本地商品，回填 product_id + goodsType
                resolveProductRef(order, accountId);
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                // BOT-O1 新订单初始化状态机：按闲鱼原始 status 映射
                order.setOrderStatus(mapToOrderStatus(order.getStatus()));
                orderMapper.insert(order);
                tryCreateVirtualShipTask(order);
                eventPublisher.publishEvent(new NotifyEvent("NEW_ORDER", accountId, accountName,
                        Map.of("accountName", accountName, "orderId", order.getOrderId(),
                                "itemTitle", str(order.getItemTitle()), "amount", str(order.getAmount()),
                                "counterparty", str(order.getCounterpartyName()), "status", str(order.getStatus()))));
            }
        }
    }

    /** BOT-O1 把闲鱼原始 status/tradeStatusEnum 映射到订单状态机枚举。
     *  闲鱼真实取值（参考 isReadyForVirtualShip L545-550 + mapStatusFromEnum）：
     *  status        — PAID/BUYER_TO_CONFIRM/SELLER_CONSIGN/TRADE_FINISHED/REFUNDING/TRADE_CLOSED 等（大写）
     *  tradeStatusEnum — buyer_to_confirm/paid/trade_paid/trade_finish/refund/refund_finish/trade_closed 等（小写） */
    private String mapToOrderStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return OrderStatusMachine.CREATED;
        String s = rawStatus.toUpperCase();
        // 待付款（下单未付）
        if (s.contains("WAIT") || s.contains("PENDING") || s.contains("CREATED") || s.contains("BUYER_PAY")) return OrderStatusMachine.CREATED;
        // 已付款（含 buyer_to_confirm/paid/trade_paid/buyer_to_confirm）
        if (s.contains("PAID") || s.contains("BUYER_TO_CONFIRM") || s.contains("TRADE_PAID")) return OrderStatusMachine.PAID;
        // 已发货（含 seller_consign/consign/shipped）
        if (s.contains("CONSIGN") || s.contains("SHIPPED") || s.contains("SELLER_CONSIGN")) return OrderStatusMachine.SHIPPED;
        // 已完成（含 trade_finish/finished，但排除 refund_finish）
        if ((s.contains("FINISH") || s.contains("TRADE_FINISHED")) && !s.contains("REFUND")) return OrderStatusMachine.COMPLETED;
        // 退款中（含 refund/refunding，但排除 refund_finish/refund_finished）
        if (s.contains("REFUND") && !(s.contains("FINISH"))) return OrderStatusMachine.REFUNDING;
        // 已退款（含 refund_finish/refund_finished）
        if (s.contains("REFUND") && s.contains("FINISH")) return OrderStatusMachine.REFUNDED;
        // 已关闭/取消
        if (s.contains("CLOSE") || s.contains("CANCEL") || s.contains("TRADE_CLOSED")) return OrderStatusMachine.CLOSED;
        // 默认按已付款（最常见同步态，与 isReadyForVirtualShip 一致）
        return OrderStatusMachine.PAID;
    }

    /** BOT-O1 调状态机服务迁移（容错：服务未注入或迁移非法不阻断主链路）。 */
    private void transitionOrderStatus(String orderId, String to) {
        if (orderStateMachineService == null) return;
        try {
            orderStateMachineService.transition(orderId, to);
        } catch (Exception e) {
            log.warn("[BOT-O1] 订单 {} 状态机迁移到 {} 失败（非致命）: {}", orderId, to, e.getMessage());
        }
    }

    private String accountName(Long accountId) {
        XianyuAccount a = accountMapper.selectById(accountId);
        if (a == null) return String.valueOf(accountId);
        return a.getDisplayName() != null ? a.getDisplayName() : a.getAccountName();
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    /**
     * 按 accountId + orderId 查询
     */
    private XianyuOrder findByAccountIdAndOrderId(Long accountId, String orderId) {
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuOrder::getAccountId, accountId)
                .eq(XianyuOrder::getOrderId, orderId)
                .last("LIMIT 1");
        return orderMapper.selectOne(wrapper);
    }

    /**
     * 优先按 accountId + itemId 精准反查本地商品，兜底按 title 匹配。
     */
    private void resolveProductRef(XianyuOrder order, Long accountId) {
        XianyuProduct product = null;
        if (order.getItemId() != null && !order.getItemId().isBlank()) {
            product = productMapper.selectOne(
                    new LambdaQueryWrapper<XianyuProduct>()
                            .eq(XianyuProduct::getAccountId, accountId)
                            .eq(XianyuProduct::getItemId, order.getItemId())
                            .last("LIMIT 1"));
        }
        if (product == null && order.getItemTitle() != null && !order.getItemTitle().isBlank()) {
            product = productMapper.selectOne(
                    new LambdaQueryWrapper<XianyuProduct>()
                            .eq(XianyuProduct::getAccountId, accountId)
                            .eq(XianyuProduct::getTitle, order.getItemTitle())
                            .last("LIMIT 1"));
        }
        if (product != null) {
            order.setProductId(product.getId());
            if (product.getGoodsType() != null) {
                order.setGoodsType(product.getGoodsType());
            }
            if ("VIRTUAL".equals(product.getGoodsType())) {
                order.setRequireVirtualShip(true);
            }
        }
    }

    private void tryCreateVirtualShipTask(XianyuOrder order) {
        if (!isReadyForVirtualShip(order)) return;
        try {
            virtualShipService.createShipTaskIfVirtual(order.getId());
        } catch (Exception e) {
            eventPublisher.publishEvent(new NotifyEvent("VIRTUAL_SHIP_FAILED", order.getAccountId(), accountName(order.getAccountId()),
                    Map.of("accountName", accountName(order.getAccountId()), "orderId", str(order.getOrderId()),
                            "itemTitle", str(order.getItemTitle()), "error", e.getMessage() != null ? e.getMessage() : "")));
        }
    }

    private boolean isReadyForVirtualShip(XianyuOrder order) {
        if (!"SOLD".equals(order.getType())) return false;
        if (!Boolean.TRUE.equals(order.getRequireVirtualShip())) return false;
        if (order.getVirtualShippedAt() != null) return false;
        String status = str(order.getStatus());
        String tradeStatus = str(order.getTradeStatusEnum());
        return "PAID".equals(status)
                || "BUYER_TO_CONFIRM".equals(status)
                || "buyer_to_confirm".equals(tradeStatus)
                || "paid".equals(tradeStatus)
                || "trade_paid".equals(tradeStatus);
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asText();
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        public boolean success;
        public String message;
        public int boughtCount;
        public int soldCount;
        public int totalCount;
        public LocalDateTime syncedAt;

        public SyncResult() {}

        public static SyncResult error(String message) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
