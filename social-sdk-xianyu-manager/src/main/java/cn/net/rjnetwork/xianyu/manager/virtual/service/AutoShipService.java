package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuTradeAuxApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.CloudStorageService;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.ship.service.DeliveryRuleEngine;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.DeliveryLogMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.DeliveryLog;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动发货主链路 —— A8。
 * <p>把支付事件 → 卡券匹配 → 规则评估 → 延迟 → 发送 → 落库 完整串起来，
 * 对标参考项目 ship_order_service.py 的主链路。原 VirtualShipService 只做 task 调度壳，
 * 本服务承载发货决策与执行，避免原 processShipTask 单卡券/无规则/无落库的缺陷。</p>
 *
 * <p>链路顺序：</p>
 * <ol>
 *   <li>拉支付成功订单（status=TRADE_PAID 或 task 关联的订单）；</li>
 *   <li>A7 规则评估：调 DeliveryRuleEngine.shouldBlock，
 *       BLOCKED → 跳过+记+通知；NOTIFY_ONLY → 发但推通知；DELAY → 写下次执行时间；</li>
 *   <li>A6 多卡券匹配：按 card_item_relation.priority 升序拿首个 AVAILABLE 的 ship_card，
 *       拿不到则 SKIPPED+记「无可用卡券」触发 A9 补发；</li>
 *   <li>延迟窗口：若 ship_config 配置了 delaySeconds，写 task.nextRunAt 延后执行；</li>
 *   <li>发送：调 MessageService.sendMessage（buyerId 兜底 session）把卡券内容发给买家；</li>
 *   <li>闲鱼确认：调 dummyDelivery（无需物流）完成平台侧发货；</li>
 *   <li>落库：写 DeliveryLog 审计 + 标 ship_card USED + 标订单 SHIPPED + 标 task SUCCESS；</li>
 *   <li>通知：VIRTUAL_SHIP_SUCCESS / VIRTUAL_SHIP_FAILED / AUTO_SHIP_* 站内站外事件。</li>
 * </ol>
 *
 * <p>批次日志复用 B9 BatchJobService（job_type=auto_ship）。</p>
 */
@Service
public class AutoShipService {

    private static final Logger log = LoggerFactory.getLogger(AutoShipService.class);
    private static final String JOB_TYPE = "auto_ship";

    private final OrderMapper orderMapper;
    private final ShipCardMapper shipCardMapper;
    private final CardItemRelationMapper relationMapper;
    private final DeliveryLogMapper deliveryLogMapper;
    private final VirtualShipTaskMapper taskMapper;
    private final AccountMapper accountMapper;
    private final ProductMapper productMapper;
    private final CloudStorageService cloudStorageService;
    private final DeliveryRuleEngine ruleEngine;
    private final MessageService messageService;
    private final BatchJobService batchJobService;
    private final ApplicationEventPublisher eventPublisher;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;
    /** self 代理，用于调用 REQUIRES_NEW 固化 MESSAGE_SENT 标记。 */
    private AutoShipService self;

    public AutoShipService(OrderMapper orderMapper,
                           ShipCardMapper shipCardMapper,
                           CardItemRelationMapper relationMapper,
                           DeliveryLogMapper deliveryLogMapper,
                           VirtualShipTaskMapper taskMapper,
                           AccountMapper accountMapper,
                           ProductMapper productMapper,
                           CloudStorageService cloudStorageService,
                           DeliveryRuleEngine ruleEngine,
                           MessageService messageService,
                           BatchJobService batchJobService,
                           ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.shipCardMapper = shipCardMapper;
        this.relationMapper = relationMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.taskMapper = taskMapper;
        this.accountMapper = accountMapper;
        this.productMapper = productMapper;
        this.cloudStorageService = cloudStorageService;
        this.ruleEngine = ruleEngine;
        this.messageService = messageService;
        this.batchJobService = batchJobService;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setSelf(@Lazy AutoShipService self) {
        this.self = self;
    }

    /**
     * 处理单个发货任务 —— A8 主链路入口。
     * 由 VirtualShipService.scanAndShip 对每个 PENDING task 调一次，也可管理端手动触发。
     * <p>事务边界：本方法<b>不</b>用 @Transactional——发货链路逐步独立落库（每步各自释放连接），
     * 不该一个长事务包到底占着唯一连接让前端别的请求全线等 30s 超时（现网症结 runningSqlCount 1: UPDATE xianyu_order raw_data 大字段卡死）。
     * 需要原子性的子步骤（固化 MESSAGE_SENT 标记等）用 markMessageSent 的独立小事务。</p>
     */
    public void processShipTask(VirtualShipTask task) {
        long t0 = System.currentTimeMillis();
        task.setStatus("PROCESSING");
        taskMapper.updateById(task);

        DeliveryLog audit = new DeliveryLog();
        audit.setAccountId(task.getAccountId());
        audit.setOrderId(task.getOrderId());
        audit.setProductId(task.getProductId());
        audit.setBatchJobId(null);
        audit.setShippedAt(LocalDateTime.now());

        try {
            XianyuOrder order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                finalizeFail(task, audit, "FAILED", "订单不存在");
                return;
            }
            audit.setBuyerId(order.getBuyerId());

            // 1. A7 规则评估
            DeliveryRuleEngine.DeliveryContext ctx = new DeliveryRuleEngine.DeliveryContext(
                    task.getAccountId(), parseLong(order.getBuyerId()), task.getProductId(),
                    order.getAmount() == null ? null : order.getAmount().doubleValue(),
                    null); // buyerRegion 暂未存于 XianyuOrder，规则REGION_BLOCK 暂以null跳过
            DeliveryRuleEngine.BlockDecision decision = ruleEngine.shouldBlock(ctx);
            audit.setRuleDecision(decision.block ? decision.action : "PASS");
            audit.setHitRuleName(decision.ruleName);
            if (decision.block && "BLOCK".equals(decision.action)) {
                finalizeSkip(task, audit, "规则拦截：" + decision.reason);
                publishNotify(task, order, "AUTO_SHIP_BLOCKED", decision.reason);
                return;
            }
            if ("DELAY".equals(decision.action)) {
                task.setStatus("PENDING");
                task.setExecuteAt(LocalDateTime.now().plusMinutes(30));
                taskMapper.updateById(task);
                audit.setStatus("DELAYED");
                audit.setFailureReason(decision.reason);
                deliveryLogMapper.insert(audit);
                return;
            }
            // NOTIFY_ONLY 继续发货但推通知
            if (decision.block && "NOTIFY_ONLY".equals(decision.action)) {
                publishNotify(task, order, "AUTO_SHIP_NOTIFY_ONLY", decision.reason);
            }

            // 消息已发成功但 dummyDelivery 失败时：重试只补平台确认，不再重复匹配卡券/重发 IM。
            boolean messageAlreadySent = task.getErrorMessage() != null
                    && task.getErrorMessage().startsWith("MESSAGE_SENT:")
                    && order.getDeliverContent() != null && !order.getDeliverContent().isBlank();

            ShipCard matched = null;
            String deliverText = order.getDeliverContent();
            if (!messageAlreadySent) {
                // 2.0 模板直发：LINK/FILE 类型不依赖卡券池，直接用商品模板渲染发货内容。
                //     CARD/ACCOUNT 才走下方 A6 多卡券匹配（每单消耗一张卡券）。
                XianyuProduct product = productMapper.selectById(task.getProductId());
                String deliverType = product != null ? product.getDeliverType() : null;
                String template = product != null ? product.getDeliverContentTemplate() : null;
                boolean templateDirect = "LINK".equals(deliverType) || "FILE".equals(deliverType);
                if (templateDirect) {
                    DeliverPayload payload = parseDeliverPayload(template);
                    if ("LINK".equals(deliverType)) {
                        String link = payload != null && !isBlank(payload.link) ? payload.link : template;
                        if (isBlank(link)) {
                            finalizeSkip(task, audit, "链接发货但商品未配置链接（deliverContentTemplate 为空）");
                            return;
                        }
                        String message = payload != null && !isBlank(payload.message) ? payload.message : link;
                        deliverText = renderTemplate(message, order, link);
                        audit.setDeliverContent(deliverText);
                    } else {
                        String filePath = payload != null && !isBlank(payload.filePath) ? payload.filePath : template;
                        deliverText = uploadFileDeliver(filePath, payload != null ? payload.message : null, product);
                        audit.setDeliverContent(deliverText);
                    }
                } else {
                    // 2. A6 多卡券匹配：按 priority 升序原子抢占 AVAILABLE→USED
                    List<CardItemRelation> rels = relationMapper.selectEnabledByProductId(task.getProductId());
                    for (CardItemRelation rel : rels) {
                        ShipCard card = shipCardMapper.selectById(rel.getCardId());
                        if (card == null || !"AVAILABLE".equals(card.getStatus())) {
                            continue;
                        }
                        // 先 CAS 预占，成功后再发送，避免并发双发同一卡
                        card.setStatus("USED");
                        card.setUsedOrderId(task.getOrderId());
                        card.setUsedAt(LocalDateTime.now());
                        int claimed = shipCardMapper.update(card, new LambdaQueryWrapper<ShipCard>()
                                .eq(ShipCard::getId, card.getId())
                                .eq(ShipCard::getStatus, "AVAILABLE"));
                        if (claimed > 0) {
                            matched = card;
                            break;
                        }
                    }
                    if (matched == null) {
                        finalizeSkip(task, audit, "无可用卡券，触发 A9 补发");
                        // 推通知让运营补卡券池；A9 补发任务也会扫到本 task 重试
                        publishNotify(task, order, "AUTO_SHIP_NO_CARD", "productId=" + task.getProductId() + " 无可用卡券");
                        return;
                    }
                    deliverText = buildDeliverContent(matched);
                    audit.setShipCardId(matched.getId());
                    audit.setDeliverContent(deliverText);
                }

                // 3. 延迟窗口（executeAt 未到则延后执行，防被风控盯上）
                if (task.getExecuteAt() != null && task.getExecuteAt().isAfter(LocalDateTime.now())) {
                    // 延迟执行：释放刚抢占的卡券，避免被长时间占用
                    if (matched != null) releaseShipCard(matched.getId());
                    task.setStatus("PENDING");
                    taskMapper.updateById(task);
                    audit.setStatus("DELAYED");
                    audit.setFailureReason("延迟至 " + task.getExecuteAt() + " 后发");
                    deliveryLogMapper.insert(audit);
                    return;
                }

                // 4. 真实发消息：buyerId 兜底生成会话，禁止 sessionId=null 假发送
                XianyuAccount account = accountMapper.selectById(task.getAccountId());
                if (account == null) {
                    if (matched != null) releaseShipCard(matched.getId());
                    finalizeFail(task, audit, "FAILED", "账号不存在");
                    publishShipFailed(task, order, "账号不存在");
                    return;
                }
                String buyerId = stripGoofishSuffix(order.getBuyerId());
                if (buyerId.isBlank()) {
                    if (matched != null) releaseShipCard(matched.getId());
                    finalizeFail(task, audit, "FAILED", "订单缺少 buyerId，无法发送发货消息");
                    publishShipFailed(task, order, "订单缺少 buyerId，无法发送发货消息");
                    return;
                }
                try {
                    MessageSendRequest req = new MessageSendRequest();
                    req.setAccountId(account.getId());
                    req.setBuyerId(buyerId);
                    // 不传 normalizeCid(buyerId) 假会话（服务端不存在该会话，买家收不到但本地假成功）。
                    // 传闲鱼订单号，由 MessageService 按订单号反查订单会话的真实会话 ID。
                    req.setOrderId(order.getOrderId());
                    req.setContent(deliverText);
                    req.setAutoReply(false);
                    // 拿到刚发出的帧 mid（MessageService.sendMessage 已把 sendFrameAsync 合成响应的 mid 落到返回值）。
                    // mid 存进 task.messageId，供 pushListener 收到服务端送达回执时按 mid 匹配回写 SUCCESS。
                    XianyuMessage sentMsg = messageService.sendMessage(req);
                    task.setMessageId(sentMsg != null ? sentMsg.getMsgId() : null);
                    task.setSentAt(LocalDateTime.now());
                } catch (Exception e) {
                    if (matched != null) releaseShipCard(matched.getId());
                    finalizeFail(task, audit, "FAILED", "发送失败：" + e.getMessage());
                    publishShipFailed(task, order, "发送失败：" + e.getMessage());
                    return;
                }

                // 消息帧已写出：独立事务固化内容 + MESSAGE_SENT 标记 + 标 task SENT_PENDING_ACK。
                // 卡券已在发送前 CAS 预占；dummyDelivery 失败时重试只补平台确认，不重复发卡/重发 IM。
                // 关键：不在这里立即标 SUCCESS——闲鱼 IM 是异步帧，写帧成功 ≠ 送达。
                // 真正的 SUCCESS 由 MessageService.pushListener 收到服务端送达回执（同 mid）后回写，
                // 或由 ShipAckTimeoutTask 超时兜底转 FAILED。
                self.markMessageSent(matched, order, task, deliverText);
            } else {
                audit.setDeliverContent(deliverText);
            }

            // 5. 闲鱼侧无需物流确认发货（dummy 平台侧辅助确认）。
            // 关键降级：dummy 失败不回滚、不阻塞发货——消息已真发给买家（第 4 步），dummy 只是平台侧状态标记，
            // 可以后台补。如果 dummy 失败就 finalizeFail+return，会把已真发的消息落库全回滚，买家收不到但本地 FAILED。
            // 现改为：dummy 失败仅记 audit 警告 + 推通知让运营后台补，task 继续走第 6 步标 SENT_PENDING_ACK。
            XianyuAccount account = accountMapper.selectById(task.getAccountId());
            if (account == null) {
                // 账号不存在是真硬错，不能降级（消息上面第 4 步已用账号发出去，这里理论上不会触发）
                finalizeFail(task, audit, "FAILED", "账号不存在");
                publishShipFailed(task, order, "账号不存在");
                return;
            }
            try {
                confirmDummyDelivery(account, order);
            } catch (Exception e) {
                log.warn("[A8] order {} dummyDelivery failed (降级为后台补，不阻塞发货): {}",
                        task.getOrderId(), e.getMessage());
                publishNotify(task, order, "AUTO_SHIP_DUMMY_FAILED",
                        "dummyDelivery 平台侧确认失败，需后台人工补：" + truncate(e.getMessage(), 200));
                // 不 return、不 finalizeFail——继续走第 6 步标 SENT_PENDING_ACK，真发消息才是发货本质
            }

            // 6. 落库 + 标订单 SHIPPED + 标 task。
            // 分两路：
            //  - 首轮发新消息（messageId 非空 + sentAt 刚写入）：标 SENT_PENDING_ACK，等服务端送达回执才 SUCCESS。
            //  - 重试只补 dummyDelivery（messageAlreadySent=true，没发新消息）：dummy 成功就是真成功，直接 SUCCESS。
            if (matched != null && !"USED".equals(matched.getStatus())) {
                matched.setStatus("USED");
                matched.setUsedOrderId(task.getOrderId());
                matched.setUsedAt(LocalDateTime.now());
                shipCardMapper.updateById(matched);
            }

            order.setVirtualShippedAt(LocalDateTime.now());
            order.setDeliverContent(deliverText);
            order.setStatus("SHIPPED");
            order.setUpdatedAt(LocalDateTime.now());
            // 关键：只更发货 4 字段，不碰 raw_data 大字段——BaseMapper.updateById 会 UPDATE 全列含 raw_data
            // （闲鱼订单原始 JSON，大字段），SQLite 写大字段慢 + 占着写锁 → 别的请求等不到连接 → 全线 30s 超时
            // （现网症结 runningSqlCount 1: UPDATE xianyu_order ... raw_data=? 卡死）。
            orderMapper.updateShipFields(order.getId(), order.getStatus(), order.getDeliverContent(),
                    order.getVirtualShippedAt(), order.getUpdatedAt());

            audit.setStatus(messageAlreadySent ? "SUCCESS" : "SENT_PENDING_ACK");
            audit.setDurationMs(System.currentTimeMillis() - t0);
            deliveryLogMapper.insert(audit);

            if (messageAlreadySent) {
                // 重试补 dummyDelivery 轮次：消息上一轮已发，本轮 dummy 成功即真成功
                task.setStatus("SUCCESS");
                task.setErrorMessage(null);
                task.setProcessedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                publishShipSuccess(task, order, deliverText);
            } else {
                // 首轮发消息：帧已写出但未收送达回执，标 SENT_PENDING_ACK。
                // SUCCESS 由 pushListener 收回执回写；超时未收由 ShipAckTimeoutTask 转 FAILED。
                task.setStatus("SENT_PENDING_ACK");
                task.setErrorMessage("MESSAGE_SENT: pending server ack");
                taskMapper.updateById(task);
                // 不推 VIRTUAL_SHIP_SUCCESS —— 没真送达就不该报喜
            }
            log.info("[A8] order {} shipped{} messageId={}", task.getOrderId(),
                    matched != null ? " via card " + matched.getId() : " (retry dummyDelivery only)",
                    task.getMessageId());
        } catch (Exception e) {
            finalizeFail(task, audit, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
            publishShipFailed(task, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[A8] processShipTask order {} failed: {}", task.getOrderId(), e.getMessage());
        }
    }

    /**
     * 独立事务固化“消息已发出”状态，避免外层 processShipTask 事务回滚抹掉幂等标记。
     * 卡券已在发送前 CAS 预占，这里只固化订单内容与 task 标记。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMessageSent(ShipCard matched, XianyuOrder order, VirtualShipTask task, String deliverText) {
        if (order != null) {
            order.setDeliverContent(deliverText);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        if (task != null) {
            task.setErrorMessage("MESSAGE_SENT: pending dummyDelivery");
            taskMapper.updateById(task);
        }
    }

    /**
     * 收到闲鱼服务端送达回执后，按 messageId 匹配本地 SENT_PENDING_ACK 的 task 回写 SUCCESS。
     * <p>由 MessageService.pushListener 收到 sendByReceiverScope 的服务端 ack 帧（带同 mid）时调用。
     * 幂等：只对 SENT_PENDING_ACK 状态的 task 生效，已 SUCCESS/FAILED 的不动。</p>
     * <p>独立事务：避免外层消息同步事务回滚抹掉送达确认。</p>
     *
     * @param messageId 发送时存入的帧 mid（sendFrameAsync 合成响应里的 mid）
     * @return 是否真回写成功（true=确有 SENT_PENDING_ACK task 命中并改为 SUCCESS；false=无命中或状态已变）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markShipSuccessByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) return false;
        List<VirtualShipTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<VirtualShipTask>()
                .eq(VirtualShipTask::getMessageId, messageId)
                .eq(VirtualShipTask::getStatus, "SENT_PENDING_ACK"));
        if (candidates.isEmpty()) return false;
        boolean any = false;
        for (VirtualShipTask task : candidates) {
            XianyuOrder order = orderMapper.selectById(task.getOrderId());
            String deliverText = order != null ? order.getDeliverContent() : "";
            task.setStatus("SUCCESS");
            task.setErrorMessage(null);
            task.setProcessedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            if (order != null) {
                // order 在 processShipTask 第 6 步已标 SHIPPED + virtualShippedAt，此处不动 order 避免脏写
                publishShipSuccess(task, order, deliverText);
            }
            any = true;
            log.info("[A8] ship success confirmed by server ack, order={} messageId={}",
                    task.getOrderId(), messageId);
        }
        return any;
    }

    /** 发送失败时释放已 CAS 预占但未实际发出的卡券。 */
    private void releaseShipCard(Long cardId) {
        if (cardId == null) {
            return;
        }
        ShipCard card = shipCardMapper.selectById(cardId);
        if (card == null || !"USED".equals(card.getStatus())) {
            return;
        }
        card.setStatus("AVAILABLE");
        card.setUsedOrderId(null);
        card.setUsedAt(null);
        shipCardMapper.updateById(card);
    }

    private void confirmDummyDelivery(XianyuAccount account, XianyuOrder order) {
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            throw new IllegalStateException("order.orderId is blank");
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("account cookie missing for dummyDelivery");
        }
        XianyuMtopApiClient mtop = xianyuMtopClientFactory.create(account);
        if (account.getImCookieHeader() != null && !account.getImCookieHeader().isBlank()) {
            mtop.setImCookieHeader(account.getImCookieHeader());
        }
        XianyuTradeAuxApiService tradeAux = new XianyuTradeAuxApiService(mtop);
        JsonNode resp = tradeAux.dummyDelivery(order.getOrderId());
        if (resp == null) {
            throw new IllegalStateException("dummyDelivery returned null response");
        }
        String ret = resp.path("ret").toString();
        if (ret.isBlank() || ret.contains("FAIL") || ret.contains("ERROR") || !ret.contains("SUCCESS")) {
            throw new IllegalStateException(truncate(ret, 300));
        }
    }

    private void publishShipSuccess(VirtualShipTask task, XianyuOrder order, String deliverText) {
        try {
            String accountName = accountName(task.getAccountId());
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("accountName", accountName);
            vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId() : String.valueOf(task.getOrderId()));
            vars.put("buyerName", order != null && order.getCounterpartyName() != null ? order.getCounterpartyName() : "");
            vars.put("itemTitle", order != null && order.getItemTitle() != null ? order.getItemTitle() : "");
            vars.put("content", deliverText != null ? deliverText : "");
            eventPublisher.publishEvent(new NotifyEvent("VIRTUAL_SHIP_SUCCESS", task.getAccountId(), accountName, vars));
        } catch (Exception ignored) {
        }
    }

    private void publishShipFailed(VirtualShipTask task, XianyuOrder order, String reason) {
        try {
            Long accountId = task != null ? task.getAccountId() : (order != null ? order.getAccountId() : null);
            String accountName = accountName(accountId);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("accountName", accountName);
            vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId()
                    : (task != null ? String.valueOf(task.getOrderId()) : ""));
            vars.put("itemTitle", order != null && order.getItemTitle() != null ? order.getItemTitle() : "");
            vars.put("reason", reason != null ? reason : "");
            eventPublisher.publishEvent(new NotifyEvent("VIRTUAL_SHIP_FAILED", accountId, accountName, vars));
        } catch (Exception ignored) {
        }
    }

    private String accountName(Long accountId) {
        if (accountId == null) {
            return "";
        }
        XianyuAccount a = accountMapper.selectById(accountId);
        if (a == null) {
            return String.valueOf(accountId);
        }
        return a.getDisplayName() != null ? a.getDisplayName() : a.getAccountName();
    }

    private static String stripGoofishSuffix(String userId) {
        if (userId == null) {
            return "";
        }
        String trimmed = userId.trim();
        int at = trimmed.indexOf('@');
        return at > 0 ? trimmed.substring(0, at) : trimmed;
    }

    private static String normalizeCid(String userId) {
        String bare = stripGoofishSuffix(userId);
        if (bare.isBlank()) {
            return "";
        }
        return bare.contains("@") ? bare : bare + "@goofish";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 拼卡券发货文本（四类型各自格式）。 */
    private String buildDeliverContent(ShipCard card) {
        return switch (Optional.ofNullable(card.getCardType()).orElse("CARD")) {
            case "ACCOUNT" -> "账号：" + nullSafe(card.getCardCode()) + "\n密码：" + nullSafe(card.getCardPassword())
                    + (card.getExtra() == null ? "" : "\n服务器：" + card.getExtra());
            case "LINK_QRCODE" -> nullSafe(card.getContent());
            case "PLAIN_TEXT" -> nullSafe(card.getContent());
            default -> "卡号：" + nullSafe(card.getCardCode()) + "\n卡密：" + nullSafe(card.getCardPassword());
        };
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** 动态表单 JSON 解析结果：{type, link, filePath, message, cards[], accounts[]} */
    private static class DeliverPayload {
        String link;
        String filePath;
        String message;
        List<String> cards;
        List<String> accounts;
    }

    /**
     * 解析动态表单组合的 JSON（前端 products/Index.vue 与 product/Index.vue 同构产出）。
     * 兼容旧格式：不是 JSON 对象或解析失败时返回 null，调用方按纯文本模板兜底。
     */
    private DeliverPayload parseDeliverPayload(String template) {
        if (template == null || template.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode node = om.readTree(template);
            if (node == null || !node.isObject()) return null;
            DeliverPayload p = new DeliverPayload();
            p.link = node.path("link").asText("");
            p.filePath = node.path("filePath").asText("");
            p.message = node.path("message").asText("");
            if (node.has("cards") && node.get("cards").isArray()) {
                p.cards = new java.util.ArrayList<>();
                node.get("cards").forEach(c -> p.cards.add(c.asText("")));
            }
            if (node.has("accounts") && node.get("accounts").isArray()) {
                p.accounts = new java.util.ArrayList<>();
                node.get("accounts").forEach(c -> p.accounts.add(c.asText("")));
            }
            return p;
        } catch (Exception e) {
            return null; // 非 JSON（旧格式纯文本/数组）→ 兜底
        }
    }

    /** 模板渲染：${itemTitle}/${orderId}/${link} 等占位符替换，未命中变量保留原占位符。 */
    private String renderTemplate(String template, XianyuOrder order, String link) {
        if (template == null) return null;
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("itemTitle", order != null && order.getItemTitle() != null ? order.getItemTitle() : "");
        vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId() : "");
        vars.put("link", link != null ? link : "");
        Pattern p = Pattern.compile("\\$\\{(\\w+)\\}");
        Matcher m = p.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = vars.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** FILE 类型发货：deliverContentTemplate 是本地文件路径，上传网盘后返回 下载链接+提取码。 */
    private String uploadFileDeliver(String filePath, String messageTemplate, XianyuProduct product) {
        if (filePath == null || filePath.isBlank()) {
            return "【系统错误】商品文件路径为空";
        }
        try {
            List<CloudStorageAccount> accounts = cloudStorageService.listAccounts(product.getAccountId());
            if (accounts.isEmpty()) {
                return "【系统忙碌】网盘账号未配置，请稍后重试";
            }
            CloudStorageAccount account = accounts.get(0);
            File file = new File(filePath);
            if (!file.exists()) {
                return "【系统错误】商品文件不存在: " + filePath;
            }
            cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest uploadReq =
                    new cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest();
            uploadReq.setFileName(file.getName());
            uploadReq.setFileSize(file.length());
            try {
                uploadReq.setMimeType(java.nio.file.Files.probeContentType(file.toPath()));
            } catch (Exception ignored) { }
            uploadReq.setTargetPath("/xianyu-virtual-ship/" + product.getId());
            uploadReq.setExpireDays(30);
            try (FileInputStream fis = new FileInputStream(file)) {
                uploadReq.setContent(fis);
                CloudStorageFile uploaded = cloudStorageService.uploadFile(account.getId(), uploadReq);
                if (uploaded != null && "COMPLETED".equals(uploaded.getUploadStatus())) {
                    String link = cloudStorageService.shareFile(uploaded.getId());
                    Map<String, String> vars = new LinkedHashMap<>();
                    vars.put("link", link != null ? link : "");
                    vars.put("extractCode", uploaded.getExtractCode() != null ? uploaded.getExtractCode() : "");
                    vars.put("fileName", uploaded.getFileName() != null ? uploaded.getFileName() : "");
                    // 用动态表单的 message（messageTemplate），不再直接渲染整个 JSON
                    String template = messageTemplate;
                    if (template == null || template.isBlank()) {
                        return String.format("下载链接：%s\n提取码：%s\n有效期：7天", link, uploaded.getExtractCode());
                    }
                    Pattern p = Pattern.compile("\\$\\{(\\w+)\\}");
                    Matcher m = p.matcher(template);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        String key = m.group(1);
                        String val = vars.getOrDefault(key, m.group(0));
                        m.appendReplacement(sb, Matcher.quoteReplacement(val));
                    }
                    m.appendTail(sb);
                    return sb.toString();
                }
            }
            return "【系统错误】文件上传失败，请稍后重试";
        } catch (Exception e) {
            log.warn("[A8] FILE deliver failed: {}", e.getMessage());
            return "【系统错误】文件发货失败，请联系客服";
        }
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private void finalizeFail(VirtualShipTask task, DeliveryLog audit, String status, String reason) {
        audit.setStatus(status);
        audit.setFailureReason(reason);
        audit.setDurationMs(0L);
        deliveryLogMapper.insert(audit);
        task.setStatus("FAILED");
        task.setErrorMessage(reason);
        taskMapper.updateById(task);
    }

    /**
     * 把订单落库写得「够稳」：遇 SQLITE_BUSY（SQLite WAL 模式下写仍单线程，竞争写锁会抛 database is locked）
     * 重试若干次，绝不因写锁让真发出去的帧回滚。
     * <p>真发帧（第 4 步）已成功后，order 标 SHIPPED 只是本地状态固化，不该因写锁竞争把整个 REQUIRES_NEW
     * 事务回滚抹掉 messageId/sentAt 落库 + 帧已真发的成果。</p>
     * <p>重试间隔退避 50ms→200ms，最多 5 次；仍不行就抛——但这是极少数情况（busy_timeout=120s 应能兜住），
     * 抛了也比静默回滚强（上层可见 errorMessage）。</p>
     */
    private void persistOrderSafely(XianyuOrder order) {
        if (order == null) return;
        Exception last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                orderMapper.updateById(order);
                return;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                boolean isBusy = msg.contains("database is locked") || msg.contains("sqlite_busy");
                if (!isBusy) throw new RuntimeException("persistOrderSafely 非写锁错：" + e.getMessage(), e);
                last = e;
                try { Thread.sleep(50L * (attempt + 1) * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new RuntimeException("persistOrderSafely 重试耗尽仍 SQLITE_BUSY：" + (last != null ? last.getMessage() : ""), last);
    }

    private void finalizeSkip(VirtualShipTask task, DeliveryLog audit, String reason) {
        audit.setStatus("SKIPPED");
        audit.setFailureReason(reason);
        audit.setDurationMs(0L);
        deliveryLogMapper.insert(audit);
        task.setStatus("SKIPPED");
        task.setErrorMessage(reason);
        taskMapper.updateById(task);
    }

    private void publishNotify(VirtualShipTask task, XianyuOrder order, String type, String reason) {
        try {
            String accountName = accountName(task.getAccountId());
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("accountName", accountName);
            vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId() : String.valueOf(task.getOrderId()));
            vars.put("productId", task.getProductId() != null ? String.valueOf(task.getProductId()) : "");
            vars.put("itemTitle", order != null && order.getItemTitle() != null ? order.getItemTitle() : "");
            vars.put("reason", reason == null ? "" : reason);
            eventPublisher.publishEvent(new NotifyEvent(type, task.getAccountId(), accountName, vars));
        } catch (Exception ignored) {
        }
    }

    /**
     * 扫超期未收服务端送达 ack 的 SENT_PENDING_ACK task，转 FAILED + 推通知。
     * <p>由 ShipAckTimeoutTask 定时调用。判定：status=SENT_PENDING_ACK 且 sentAt 已过 {@code staleSeconds} 秒。
     * 转 FAILED 后会被 RedeliveryService.collectCandidates 扫到（已纳入 SENT_PENDING_ACK），按指数退避重发。</p>
     * <p>幂等：只动 SENT_PENDING_ACK 状态的 task，已 SUCCESS（收到 ack）/ FAILED（已转）的不动。</p>
     *
     * @param staleSeconds 发出后多少秒未收 ack 即判超时
     * @return 本次扫到的超期 task 数（已转 FAILED 的）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markStaleSentPendingAckAsFailed(int staleSeconds) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(staleSeconds, 1));
        List<VirtualShipTask> stales = taskMapper.selectList(new LambdaQueryWrapper<VirtualShipTask>()
                .eq(VirtualShipTask::getStatus, "SENT_PENDING_ACK")
                .isNotNull(VirtualShipTask::getSentAt)
                .lt(VirtualShipTask::getSentAt, cutoff));
        if (stales.isEmpty()) return 0;
        for (VirtualShipTask task : stales) {
            task.setStatus("FAILED");
            // 保留原 MESSAGE_SENT 前缀语义：上一轮 errorMessage 已是 "MESSAGE_SENT: pending server ack"，
            // 此处追加超时原因，让 RedeliveryService 走「只补 dummyDelivery」分支还是「重发」分支能据此判断
            String prev = task.getErrorMessage();
            String reason = "送达回执超时未收（" + staleSeconds + "s），判定服务端静默丢帧";
            task.setErrorMessage(prev == null || prev.isBlank() ? reason
                    : (prev.startsWith("MESSAGE_SENT:") ? prev + " | " + reason : reason));
            taskMapper.updateById(task);
            XianyuOrder order = orderMapper.selectById(task.getOrderId());
            publishShipFailed(task, order, reason);
            log.warn("[A8] ship ack timeout, order={} messageId={} sentAt={} → FAILED",
                    task.getOrderId(), task.getMessageId(), task.getSentAt());
        }
        return stales.size();
    }

    /** 批次入口：扫所有 PENDING task 跑一遍，给 VirtualShipService.scanAndShip 调。 */
    public Long runBatch(String triggerSource) {
        List<VirtualShipTask> pendings = taskMapper.selectList(new LambdaQueryWrapper<VirtualShipTask>()
                .eq(VirtualShipTask::getStatus, "PENDING")
                .and(w -> w.isNull(VirtualShipTask::getExecuteAt)
                        .or().le(VirtualShipTask::getExecuteAt, LocalDateTime.now())));
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, pendings.size());
        int success = 0, failed = 0, skipped = 0;
        for (VirtualShipTask t : pendings) {
            long t0 = System.currentTimeMillis();
            try {
                processShipTask(t);
                String st = t.getStatus();
                if ("SUCCESS".equals(st)) {
                    success++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "SUCCESS", System.currentTimeMillis() - t0, null, null);
                } else if ("SKIPPED".equals(st) || "DELAYED".equals(st)) {
                    skipped++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "SKIPPED", System.currentTimeMillis() - t0,
                            t.getErrorMessage(), null);
                } else {
                    failed++;
                    batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                            "order#" + t.getOrderId(), "FAILED", System.currentTimeMillis() - t0,
                            t.getErrorMessage(), null);
                }
            } catch (Exception e) {
                failed++;
                batchJobService.recordItem(job.getId(), String.valueOf(t.getOrderId()),
                        "order#" + t.getOrderId(), "FAILED", System.currentTimeMillis() - t0,
                        e.getMessage(), null);
            }
        }
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", pendings.size(), success, failed, skipped));
        return job.getId();
    }

    /** 供消息同步链路在收到支付事件时直接触发：建 task → 入队 → 立即跑。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaySuccess(Long accountId, Long orderId, Long productId, String sessionId) {
        VirtualShipTask task = new VirtualShipTask();
        task.setAccountId(accountId);
        task.setOrderId(orderId);
        task.setProductId(productId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetry(5);
        taskMapper.insert(task);
        log.info("[A8] pay success event → enqueue ship task orderId={} productId={}", orderId, productId);
        // 立即跑一次（链路内含延迟/规则，不会无脑发）
        processShipTask(task);
    }

    /** 兜底用：扫订单表里 PAID 但还没建 task 的，补建入队。由 scanAndShip 调。 */
    public int enqueuePaidOrders() {
        List<XianyuOrder> paid = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getStatus, "PAID"));
        int enqueued = 0;
        for (XianyuOrder o : paid) {
            // 已有同订单 PENDING/SUCCESS task 则跳过
            Long exists = taskMapper.selectCount(new LambdaQueryWrapper<VirtualShipTask>()
                    .eq(VirtualShipTask::getOrderId, parseLongOrderId(o.getOrderId()))
                    .in(VirtualShipTask::getStatus, "PENDING", "PROCESSING", "SUCCESS"));
            if (exists != null && exists > 0) continue;
            VirtualShipTask task = new VirtualShipTask();
            task.setAccountId(o.getAccountId());
            task.setOrderId(parseLongOrderId(o.getOrderId()));
            task.setProductId(o.getProductId());
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setMaxRetry(5);
            taskMapper.insert(task);
            enqueued++;
        }
        return enqueued;
    }

    /** XianyuOrder.orderId 是 String 类型（闲鱼侧长 ID），转 Long 存 VirtualShipTask.orderId。 */
    private Long parseLongOrderId(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) {
            // 非纯数字的闲鱼 orderId 用 hashCode 兜底（碰撞概率低，仅用于 task 关联）
            return (long) s.hashCode();
        }
    }
}
