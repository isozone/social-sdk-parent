package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuTradeAuxApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.CloudStorageService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualCardPoolMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipConfigMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualCardPool;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动发货引擎（虚拟商品）
 * 核心职责：
 * 1. 订单支付后创建虚拟发货任务
 * 2. 定时扫描并执行发货（真实 IM 消息 + 闲鱼 dummyDelivery）
 * 3. 定时扫描并执行自动确认收货
 * 4. 卡密池扣减
 * 5. 发货成功/失败发布站内/站外通知
 */
@Service
public class VirtualShipService {

    private static final Logger log = LoggerFactory.getLogger(VirtualShipService.class);
    private static final Pattern CARD_PATTERN = Pattern.compile("^(.+?)(?:\\|(.+))?$");

    private final VirtualShipTaskMapper shipTaskMapper;
    private final VirtualCardPoolMapper cardPoolMapper;
    private final VirtualShipConfigMapper shipConfigMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    /** A6 新卡券模型：ship_card + card_item_relation（AutoShipService 发货读取，与旧 virtual_card_pool 并存兼容） */
    private ShipCardMapper shipCardMapper;
    private CardItemRelationMapper cardItemRelationMapper;
    /** 真实消息发送器（DefaultVirtualMessageSender → MessageService.sendMessage） */
    private final VirtualMessageSender messageSender;
    /** 网盘存储服务（FILE 类型发货） */
    private final CloudStorageService cloudStorageService;
    private final ApplicationEventPublisher eventPublisher;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;
    /** 统一发货执行引擎：调度/补发/手动触发都收敛到 AutoShipService，避免双引擎重复发货。 */
    private AutoShipService autoShipService;
    /** self 代理，用于调用 REQUIRES_NEW 固化 MESSAGE_SENT 标记。 */
    private VirtualShipService self;

    public VirtualShipService(VirtualShipTaskMapper shipTaskMapper,
                              VirtualCardPoolMapper cardPoolMapper,
                              VirtualShipConfigMapper shipConfigMapper,
                              ProductMapper productMapper,
                              OrderMapper orderMapper,
                              AccountMapper accountMapper,
                              VirtualMessageSender messageSender,
                              CloudStorageService cloudStorageService,
                              ApplicationEventPublisher eventPublisher) {
        this.shipTaskMapper = shipTaskMapper;
        this.cardPoolMapper = cardPoolMapper;
        this.shipConfigMapper = shipConfigMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.messageSender = messageSender;
        this.cloudStorageService = cloudStorageService;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setAutoShipService(@Lazy AutoShipService autoShipService) {
        this.autoShipService = autoShipService;
    }

    @Autowired
    public void setShipCardMapper(ShipCardMapper shipCardMapper) {
        this.shipCardMapper = shipCardMapper;
    }

    @Autowired
    public void setCardItemRelationMapper(CardItemRelationMapper cardItemRelationMapper) {
        this.cardItemRelationMapper = cardItemRelationMapper;
    }

    @Autowired
    public void setSelf(@Lazy VirtualShipService self) {
        this.self = self;
    }

    // ======================================================================
    // 对外接口：订单支付后调用
    // ======================================================================

    /**
     * 支付成功后调用：如果需要虚拟发货，则创建发货任务。
     * <p>商品来源优先级：order.productId（订单同步时已回填）→ 按 itemTitle + accountId 反查兜底。</p>
     */
    @Transactional
    public VirtualShipTask createShipTaskIfVirtual(Long orderId) {
        XianyuOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);
        if (!Boolean.TRUE.equals(order.getRequireVirtualShip())) return null;

        // 优先用 order.productId 直查商品；查不到则按 title 兜底
        XianyuProduct product = null;
        if (order.getProductId() != null) {
            product = productMapper.selectById(order.getProductId());
        }
        if (product == null && order.getItemTitle() != null) {
            product = productMapper.selectOne(
                    new LambdaQueryWrapper<XianyuProduct>()
                            .eq(XianyuProduct::getAccountId, order.getAccountId())
                            .eq(XianyuProduct::getTitle, order.getItemTitle())
                            .last("LIMIT 1"));
        }
        if (product == null || !"VIRTUAL".equals(product.getGoodsType())) return null;

        // 幂等：已存在任务则不重复创建
        VirtualShipTask existing = shipTaskMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipTask>().eq(VirtualShipTask::getOrderId, orderId));
        if (existing != null) return existing;

        VirtualShipConfig config = getConfig(order.getAccountId());
        if (config != null && Boolean.FALSE.equals(config.getEnabled())) return null;
        int delaySeconds = config != null && config.getDelaySeconds() != null ? Math.max(config.getDelaySeconds(), 0) : 0;
        LocalDateTime now = LocalDateTime.now();

        VirtualShipTask task = new VirtualShipTask();
        task.setOrderId(orderId);
        task.setAccountId(order.getAccountId()); // 必须写 accountId，否则 AutoShipService 发货时查不到账号
        task.setProductId(product.getId());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setExecuteAt(now.plusSeconds(delaySeconds));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        shipTaskMapper.insert(task);

        // 更新订单虚拟发货相关字段
        order.setGoodsType("VIRTUAL");
        order.setProductId(product.getId());
        order.setRequireVirtualShip(true);
        if (config != null && config.getAutoConfirmDays() != null) {
            order.setAutoReceiptAt(LocalDateTime.now().plusDays(config.getAutoConfirmDays()));
        }
        orderMapper.updateById(order);

        return task;
    }

    // ======================================================================
    // 定时任务：扫描并执行发货
    // ======================================================================

    /**
     * 扫描到期的待发货任务，由 ScheduledTasks 统一调度。
     * <p>统一委托 AutoShipService，避免 VirtualShipService/AutoShipService 双引擎并发发货。</p>
     */
    public void scanAndShip() {
        if (autoShipService == null) {
            log.warn("[VirtualShip] AutoShipService not ready, skip scanAndShip");
            return;
        }
        autoShipService.runBatch("SCHEDULE");
    }

    /**
     * 扫描「已付款待发货」的虚拟订单，为缺失发货任务的订单补建任务。
     * <p>兜底链路：订单同步解析出错（如 sold 结构字段路径不匹配）时，
     * tryCreateVirtualShipTask 可能漏建任务，本方法定时补偿，确保已付款订单最终进入发货队列。
     * 与 isReadyForVirtualShip 判定条件保持一致（type=SOLD / requireVirtualShip / 未发货 / 已付款）。</p>
     *
     * @return 本次补建的任务数
     */
    public int scanPaidOrdersAndCreateTasks() {
        List<XianyuOrder> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getType, "SOLD")
                        .eq(XianyuOrder::getRequireVirtualShip, true)
                        .isNull(XianyuOrder::getVirtualShippedAt)
                        .and(w -> w.eq(XianyuOrder::getStatus, "PAID")
                                .or().eq(XianyuOrder::getStatus, "BUYER_TO_CONFIRM")
                                .or().eq(XianyuOrder::getTradeStatusEnum, "paid")
                                .or().eq(XianyuOrder::getTradeStatusEnum, "trade_paid")
                                .or().eq(XianyuOrder::getTradeStatusEnum, "buyer_to_confirm"))
                        .last("LIMIT 50"));
        int created = 0;
        for (XianyuOrder order : paidOrders) {
            // 已存在任务则跳过（createShipTaskIfVirtual 内部也幂等）
            VirtualShipTask existing = shipTaskMapper.selectOne(
                    new LambdaQueryWrapper<VirtualShipTask>()
                            .eq(VirtualShipTask::getOrderId, order.getId()));
            if (existing != null) continue;
            try {
                createShipTaskIfVirtual(order.getId());
                created++;
            } catch (Exception e) {
                log.warn("[VirtualShip] 补建发货任务失败 orderId={}: {}", order.getOrderId(), e.getMessage());
            }
        }
        if (created > 0) {
            log.info("[VirtualShip] 扫描待发货订单补建任务 {} 个", created);
        }
        return created;
    }

    /**
     * 扫描失败任务重试（最多重试 3 次），由 ScheduledTasks 统一调度。
     * <p>统一委托 AutoShipService，保持 MESSAGE_SENT 幂等标记语义一致。</p>
     */
    public void retryFailedShipTasks() {
        List<VirtualShipTask> failed = shipTaskMapper.selectList(
                new LambdaQueryWrapper<VirtualShipTask>()
                        .eq(VirtualShipTask::getStatus, "FAILED")
                        .lt(VirtualShipTask::getRetryCount, 3)
                        .last("LIMIT 10"));
        for (VirtualShipTask task : failed) {
            processShipTask(task);
        }
    }

    /**
     * 对外统一入口：所有发货执行收敛到 AutoShipService。
     * 保留方法签名，兼容 Controller / 旧调用方。
     */
    public void processShipTask(VirtualShipTask task) {
        if (autoShipService == null) {
            throw new IllegalStateException("AutoShipService not ready");
        }
        autoShipService.processShipTask(task);
    }

    /**
     * 兼容保留：旧链路直发实现。当前运行时不再被调度直接调用。
     */
    @Transactional
    public void processShipTaskLegacy(VirtualShipTask task) {
        task.setStatus("PROCESSING");
        shipTaskMapper.updateById(task);

        // task.orderId 存的是本地 xianyu_order.id（由 OrderSyncService.tryCreateVirtualShipTask 写入）
        XianyuOrder order = orderMapper.selectById(task.getOrderId());
        XianyuProduct product = productMapper.selectById(task.getProductId());
        if (order == null || product == null) {
            failTask(task, "Order or product not found");
            publishShipFailed(task, order, "Order or product not found");
            return;
        }

        boolean messageAlreadySent = task.getErrorMessage() != null
                && task.getErrorMessage().startsWith("MESSAGE_SENT:")
                && order.getDeliverContent() != null && !order.getDeliverContent().isBlank();
        String deliverContent = order.getDeliverContent();
        try {
            if (!messageAlreadySent) {
                deliverContent = acquireDeliverContent(order, product);
                if (deliverContent == null || deliverContent.isBlank()) {
                    failTask(task, "No available card/content in pool");
                    publishShipFailed(task, order, "No available card/content in pool");
                    return;
                }

                // 1) 真实发消息给买家（失败不标已发货，已预占卡密会释放）
                boolean sent = messageSender.sendToBuyer(order, deliverContent);
                if (!sent) {
                    releaseVirtualCard(order.getId());
                    failTask(task, "sendToBuyer returned false");
                    publishShipFailed(task, order, "sendToBuyer returned false");
                    return;
                }

                // 消息已发出后，独立事务固化发货内容与 MESSAGE_SENT 标记；
                // 避免后续 dummyDelivery 失败回滚导致重试重复发卡/重复发 IM。
                self.markMessageSent(order, task, deliverContent);
            }

            // 2) 闲鱼侧无需物流确认发货（dummyDelivery）
            confirmDummyDelivery(order);

            // 3) 本地订单/任务成功落库
            order.setVirtualShippedAt(LocalDateTime.now());
            order.setDeliverContent(deliverContent);
            order.setStatus("SHIPPED");
            orderMapper.updateById(order);

            task.setStatus("SHIPPED");
            task.setErrorMessage(null);
            task.setProcessedAt(LocalDateTime.now());
            shipTaskMapper.updateById(task);

            publishShipSuccess(task, order, product);
            log.info("[VirtualShip] shipped orderId={} localOrderId={} accountId={}",
                    order.getOrderId(), order.getId(), order.getAccountId());

        } catch (Exception e) {
            if (!messageAlreadySent && (task.getErrorMessage() == null || !task.getErrorMessage().startsWith("MESSAGE_SENT:"))) {
                releaseVirtualCard(order.getId());
            }
            String reason = (task.getErrorMessage() != null && task.getErrorMessage().startsWith("MESSAGE_SENT:"))
                    ? "MESSAGE_SENT: dummyDelivery failed: " + e.getMessage()
                    : e.getMessage();
            failTask(task, reason);
            publishShipFailed(task, order, e.getMessage());
            log.warn("[VirtualShip] processShipTask failed orderId={} err={}",
                    order != null ? order.getOrderId() : task.getOrderId(), e.getMessage());
        }
    }

    /**
     * 独立事务固化“消息已发出、等待 dummyDelivery”状态，避免外层事务回滚抹掉幂等标记。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMessageSent(XianyuOrder order, VirtualShipTask task, String deliverContent) {
        if (order != null) {
            order.setDeliverContent(deliverContent);
            orderMapper.updateById(order);
        }
        if (task != null) {
            task.setErrorMessage("MESSAGE_SENT: pending dummyDelivery");
            shipTaskMapper.updateById(task);
        }
    }

    private void releaseVirtualCard(Long orderId) {
        if (orderId == null) return;
        VirtualCardPool card = cardPoolMapper.selectOne(new LambdaQueryWrapper<VirtualCardPool>()
                .eq(VirtualCardPool::getUsedOrderId, orderId)
                .eq(VirtualCardPool::getStatus, "USED")
                .last("LIMIT 1"));
        if (card == null) return;
        card.setStatus("AVAILABLE");
        card.setUsedOrderId(null);
        card.setUsedAt(null);
        cardPoolMapper.updateById(card);
    }

    /**
     * 调闲鱼 mtop.taobao.idle.logistic.consign.dummy 完成无需物流发货确认。
     * 失败直接抛异常，由上层 failTask，避免本地假成功。
     */
    private void confirmDummyDelivery(XianyuOrder order) {
        if (order.getAccountId() == null) {
            throw new IllegalStateException("order.accountId is blank");
        }
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            throw new IllegalStateException("order.orderId is blank");
        }
        XianyuAccount acc = accountMapper.selectById(order.getAccountId());
        if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalStateException("account cookie missing for dummyDelivery");
        }
        XianyuMtopApiClient mtop = xianyuMtopClientFactory.create(acc);
        if (acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank()) {
            mtop.setImCookieHeader(acc.getImCookieHeader());
        }
        XianyuTradeAuxApiService tradeAux = new XianyuTradeAuxApiService(mtop);
        JsonNode resp = tradeAux.dummyDelivery(order.getOrderId());
        if (resp == null) {
            throw new IllegalStateException("dummyDelivery returned null response");
        }
        String ret = resp.path("ret").toString();
        if (ret.isBlank() || ret.contains("FAIL") || ret.contains("ERROR") || !ret.contains("SUCCESS")) {
            throw new IllegalStateException("dummyDelivery failed: " + truncate(ret, 300));
        }
        log.info("[VirtualShip] dummyDelivery ok orderId={} ret={}", order.getOrderId(), truncate(ret, 120));
    }

    private void publishShipSuccess(VirtualShipTask task, XianyuOrder order, XianyuProduct product) {
        try {
            String accountName = accountName(order != null ? order.getAccountId() : task.getAccountId());
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("accountName", accountName);
            vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId() : String.valueOf(task.getOrderId()));
            vars.put("buyerName", order != null && order.getCounterpartyName() != null ? order.getCounterpartyName() : "");
            vars.put("itemTitle", product != null && product.getTitle() != null ? product.getTitle()
                    : (order != null && order.getItemTitle() != null ? order.getItemTitle() : ""));
            eventPublisher.publishEvent(new NotifyEvent("VIRTUAL_SHIP_SUCCESS",
                    order != null ? order.getAccountId() : task.getAccountId(),
                    accountName, vars));
        } catch (Exception e) {
            log.warn("[VirtualShip] publish success notify failed: {}", e.getMessage());
        }
    }

    private void publishShipFailed(VirtualShipTask task, XianyuOrder order, String reason) {
        try {
            Long accountId = order != null ? order.getAccountId() : (task != null ? task.getAccountId() : null);
            String accountName = accountName(accountId);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("accountName", accountName);
            vars.put("orderId", order != null && order.getOrderId() != null ? order.getOrderId()
                    : (task != null ? String.valueOf(task.getOrderId()) : ""));
            vars.put("itemTitle", order != null && order.getItemTitle() != null ? order.getItemTitle() : "");
            vars.put("reason", reason != null ? reason : "");
            eventPublisher.publishEvent(new NotifyEvent("VIRTUAL_SHIP_FAILED", accountId, accountName, vars));
        } catch (Exception e) {
            log.warn("[VirtualShip] publish fail notify failed: {}", e.getMessage());
        }
    }

    private String accountName(Long accountId) {
        if (accountId == null) return "";
        XianyuAccount a = accountMapper.selectById(accountId);
        if (a == null) return String.valueOf(accountId);
        return a.getDisplayName() != null ? a.getDisplayName() : a.getAccountName();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 生成发货内容。
     * <p>支持模板变量占位符，由 deliverContentTemplate 驱动；无模板时走默认格式。
     * 占位符：
     * <ul>
     *   <li>CARD/ACCOUNT: ${cardCode} ${cardPassword}</li>
     *   <li>FILE(网盘): ${link} ${extractCode} ${fileName}</li>
     *   <li>通用: ${itemTitle} ${orderId}</li>
     * </ul></p>
     */
    @Transactional
    protected String acquireDeliverContent(XianyuOrder order, XianyuProduct product) {
        String type = product.getDeliverType();
        String template = product.getDeliverContentTemplate();

        // 通用变量
        java.util.Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put("itemTitle", order.getItemTitle() != null ? order.getItemTitle() : "");
        vars.put("orderId", order.getOrderId() != null ? order.getOrderId() : "");

        if ("CARD".equals(type) || "ACCOUNT".equals(type)) {
            // 原子抢占卡密：先查候选，再 CAS 更新 AVAILABLE→USED，避免并发双发同一卡。
            List<VirtualCardPool> candidates = cardPoolMapper.selectList(
                    new LambdaQueryWrapper<VirtualCardPool>()
                            .eq(VirtualCardPool::getProductId, product.getId())
                            .eq(VirtualCardPool::getStatus, "AVAILABLE")
                            .orderByAsc(VirtualCardPool::getId)
                            .last("LIMIT 20"));
            VirtualCardPool card = null;
            for (VirtualCardPool candidate : candidates) {
                candidate.setStatus("USED");
                candidate.setUsedOrderId(order.getId());
                candidate.setUsedAt(LocalDateTime.now());
                int claimed = cardPoolMapper.update(candidate, new LambdaQueryWrapper<VirtualCardPool>()
                        .eq(VirtualCardPool::getId, candidate.getId())
                        .eq(VirtualCardPool::getStatus, "AVAILABLE"));
                if (claimed > 0) {
                    card = candidate;
                    break;
                }
            }
            if (card == null) return null;

            vars.put("cardCode", card.getCardCode() != null ? card.getCardCode() : "");
            vars.put("cardPassword", card.getCardPassword() != null ? card.getCardPassword() : "");

            // 无模板走默认格式
            if (template == null || template.isBlank()) {
                if (card.getCardPassword() != null && !card.getCardPassword().isBlank()) {
                    return String.format("卡号：%s\n密码：%s", card.getCardCode(), card.getCardPassword());
                }
                return card.getCardCode();
            }
            return renderTemplate(template, vars);
        }

        if ("LINK".equals(type)) {
            // LINK 直接把模板当发货内容（支持 ${itemTitle} 等通用占位符）
            if (template == null || template.isBlank()) return null;
            return renderTemplate(template, vars);
        }

        if ("FILE".equals(type)) {
            // FILE: deliverContentTemplate 是本地文件路径，上传网盘后拿 link + extractCode
            String filePath = template;
            if (filePath == null || filePath.isBlank()) {
                return null;
            }
            try {
                // 1. 查找该商品的网盘账号（取第一个可用的）
                List<CloudStorageAccount> accounts = cloudStorageService.listAccounts(product.getAccountId());
                if (accounts.isEmpty()) {
                    return "【系统忙碌】网盘账号未配置，请稍后重试";
                }
                CloudStorageAccount account = accounts.get(0);

                // 2. 上传文件到网盘
                java.io.File file = new java.io.File(filePath);
                if (!file.exists()) {
                    return "【系统错误】商品文件不存在: " + filePath;
                }
                cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest uploadReq =
                        new cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest();
                uploadReq.setFileName(file.getName());
                uploadReq.setFileSize(file.length());
                uploadReq.setMimeType(java.nio.file.Files.probeContentType(file.toPath()));
                uploadReq.setTargetPath("/xianyu-virtual-ship/" + product.getId());
                uploadReq.setExpireDays(30);
                try (FileInputStream fis = new FileInputStream(file)) {
                    uploadReq.setContent(fis);
                    CloudStorageFile uploaded = cloudStorageService.uploadFile(account.getId(), uploadReq);
                    if (uploaded != null && "COMPLETED".equals(uploaded.getUploadStatus())) {
                        String link = cloudStorageService.shareFile(uploaded.getId());
                        vars.put("link", link != null ? link : "");
                        vars.put("extractCode", uploaded.getExtractCode() != null ? uploaded.getExtractCode() : "");
                        vars.put("fileName", uploaded.getFileName() != null ? uploaded.getFileName() : "");
                        // 无模板走默认格式
                        if (template == null || template.isBlank()) {
                            return String.format("下载链接：%s\n提取码：%s\n有效期：7天",
                                    link, uploaded.getExtractCode());
                        }
                        return renderTemplate(template, vars);
                    }
                }
                return "【系统错误】文件上传失败，请稍后重试";
            } catch (Exception e) {
                System.err.println("[VirtualShip] FILE deliver failed: " + e.getMessage());
                return "【系统错误】文件发货失败，请联系客服";
            }
        }

        return null;
    }

    /**
     * 模板渲染：把 ${key} 替换为 vars.get(key)，未命中变量保留原占位符。
     */
    private String renderTemplate(String template, java.util.Map<String, String> vars) {
        if (template == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$\\{(\\w+)\\}");
        java.util.regex.Matcher m = p.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = vars.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void failTask(VirtualShipTask task, String error) {
        task.setStatus("FAILED");
        task.setErrorMessage(error);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setProcessedAt(LocalDateTime.now());
        shipTaskMapper.updateById(task);
    }

    // ======================================================================
    // 定时任务：自动确认收货
    // ======================================================================

    /**
     * 扫描需要确认收货的订单，由 ScheduledTasks 统一调度。
     */
    public void autoConfirmReceipt() {
        List<XianyuOrder> toConfirm = orderMapper.selectList(
                new LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getRequireVirtualShip, true)
                        .ne(XianyuOrder::getStatus, "COMPLETED")
                        .le(XianyuOrder::getAutoReceiptAt, LocalDateTime.now())
                        .last("LIMIT 50"));
        for (XianyuOrder order : toConfirm) {
            try {
                order.setStatus("COMPLETED");
                orderMapper.updateById(order);
                // 真实场景：调用闲鱼 sdk 确认收货
            } catch (Exception e) {
                System.err.println("[VirtualShip] auto confirm failed for order " + order.getId() + ": " + e.getMessage());
            }
        }
    }

    // ======================================================================
    // 卡密池管理
    // ======================================================================

    @Transactional
    public int importCards(Long productId, List<String> cards) {
        int count = 0;
        for (String line : cards) {
            if (line == null || line.isBlank()) continue;
            Matcher m = CARD_PATTERN.matcher(line.trim());
            if (!m.matches()) continue;
            VirtualCardPool card = new VirtualCardPool();
            card.setProductId(productId);
            card.setCardCode(m.group(1).trim());
            card.setCardPassword(m.group(2) != null ? m.group(2).trim() : null);
            card.setStatus("AVAILABLE");
            card.setCreatedAt(LocalDateTime.now());
            try {
                cardPoolMapper.insert(card);
                count++;
            } catch (Exception e) {
                System.err.println("[VirtualShip] import card failed: " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * 导入卡密/账号到 A6 新卡券模型（ship_card + card_item_relation）——本地商品发布建池用。
     * <p>与 AutoShipService 发货读取的表一致（旧 importCards 写 virtual_card_pool，仅历史兼容）。
     * 每行格式：CARD→卡号|密码，ACCOUNT→账号|密码|服务器。</p>
     */
    @Transactional
    public int importShipCards(Long productId, String deliverType, List<String> cards) {
        if (shipCardMapper == null || cardItemRelationMapper == null) {
            throw new IllegalStateException("ship_card 模型未初始化（ShipCardMapper 未注入）");
        }
        int count = 0;
        String cardType = "ACCOUNT".equals(deliverType) ? "ACCOUNT" : "CARD";
        for (String line : cards) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.trim().split("\\|");
            if (parts.length == 0 || parts[0].trim().isEmpty()) continue;
            ShipCard shipCard = new ShipCard();
            shipCard.setCardType(cardType);
            shipCard.setCardCode(parts[0].trim());
            shipCard.setCardPassword(parts.length > 1 ? parts[1].trim() : null);
            if (parts.length > 2) shipCard.setExtra(parts[2].trim());
            shipCard.setStatus("AVAILABLE");
            shipCard.setCreatedAt(LocalDateTime.now());
            try {
                shipCardMapper.insert(shipCard);
                CardItemRelation rel = new CardItemRelation();
                rel.setProductId(productId);
                rel.setCardId(shipCard.getId());
                rel.setPriority(0);
                rel.setEnabled(1);
                cardItemRelationMapper.insert(rel);
                count++;
            } catch (Exception e) {
                System.err.println("[VirtualShip] import ship card failed: " + e.getMessage());
            }
        }
        return count;
    }

    // ======================================================================
    // 配置管理
    // ======================================================================

    public VirtualShipConfig getConfig(Long accountId) {
        return shipConfigMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipConfig>()
                        .eq(VirtualShipConfig::getAccountId, accountId));
    }

    @Transactional
    public VirtualShipConfig saveConfig(Long accountId, Boolean enabled, Integer delaySeconds,
                                        Integer autoConfirmDays, Boolean notifyAfterShip) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        VirtualShipConfig config = getConfig(accountId);
        if (config == null) {
            config = new VirtualShipConfig();
            config.setAccountId(accountId);
            config.setEnabled(enabled != null ? enabled : true);
            config.setDelaySeconds(delaySeconds != null ? delaySeconds : 30);
            config.setAutoConfirmDays(autoConfirmDays != null ? autoConfirmDays : 7);
            config.setNotifyAfterShip(notifyAfterShip != null ? notifyAfterShip : true);
            config.setCreatedAt(LocalDateTime.now());
            shipConfigMapper.insert(config);
        } else {
            if (enabled != null) config.setEnabled(enabled);
            if (delaySeconds != null) config.setDelaySeconds(delaySeconds);
            if (autoConfirmDays != null) config.setAutoConfirmDays(autoConfirmDays);
            if (notifyAfterShip != null) config.setNotifyAfterShip(notifyAfterShip);
            shipConfigMapper.updateById(config);
        }
        return config;
    }

    // ======================================================================
    // 查询
    // ======================================================================

    public List<VirtualShipTask> listTasks(String status, int page, int size) {
        LambdaQueryWrapper<VirtualShipTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) wrapper.eq(VirtualShipTask::getStatus, status);
        wrapper.orderByDesc(VirtualShipTask::getCreatedAt);
        // 用 Page 分页
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VirtualShipTask> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        return shipTaskMapper.selectPage(p, wrapper).getRecords();
    }

    /**
     * 手动触发发货任务（从 PENDING 状态重新执行 processShipTask）
     */
    @Transactional
    public void triggerTask(Long taskId) {
        VirtualShipTask task = shipTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not PENDING, current status: " + task.getStatus());
        }
        processShipTask(task);
    }

    /**
     * 人工发货重试：只要订单未闭环即可重试，不限制任务状态。
     * <p>PENDING / FAILED / 消息已发待平台确认(MESSAGE_SENT) / 已发货(SUCCESS) 等任意状态都允许重置为 PENDING 并立即重跑，
     * 用于买家未收到卡密等情况下的补发/重发；仅当订单已闭环（COMPLETED/CLOSED/REFUNDED）时拒绝。</p>
     * <p>关键语义：MESSAGE_SENT 标记（errorMessage 以 "MESSAGE_SENT:" 开头）保留不清，
     * AutoShipService 识别后只补 dummyDelivery 平台确认，不重复匹配卡券/重发 IM；
     * 其余状态（含 SUCCESS 重发）则走完整发货链路（重新匹配卡券/模板 → 发消息 → 平台确认）。</p>
     */
    @Transactional
    public void retryTaskForShip(Long taskId) {
        VirtualShipTask task = shipTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        // 以订单是否闭环为准：未闭环不限制任务状态（含 SUCCESS 可补发/重发）
        XianyuOrder order = task.getOrderId() == null ? null : orderMapper.selectById(task.getOrderId());
        if (order == null) {
            throw new IllegalArgumentException("任务关联订单不存在，无法人工发货");
        }
        if ("COMPLETED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            throw new IllegalStateException("订单已闭环（交易成功/已关闭/退款成功），不可再人工发货");
        }
        task.setStatus("PENDING");
        task.setExecuteAt(null); // 立即执行，跳过延迟窗口
        shipTaskMapper.updateById(task);
        processShipTask(task);
    }

    public List<VirtualCardPool> listCards(Long productId, String status) {
        LambdaQueryWrapper<VirtualCardPool> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) wrapper.eq(VirtualCardPool::getProductId, productId);
        if (status != null && !status.isBlank()) wrapper.eq(VirtualCardPool::getStatus, status);
        wrapper.orderByDesc(VirtualCardPool::getCreatedAt);
        return cardPoolMapper.selectList(wrapper);
    }

    public int deleteCard(Long id) {
        return cardPoolMapper.deleteById(id);
    }
}
