package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipConfigMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动确认收货服务 —— A10。
 * <p>定时扫「已发货 N 天（autoConfirmDays）但买家未确认」的订单，
 * 按 {@link VirtualShipConfig#getConfirmReceiptMessage} 模板发话术催买家确认收货，
 * 提升店铺评分回收速度。对标参考项目 confirm_receipt_message_service.py。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫 XianyuOrder：status=SHIPPED 且 autoReceiptAt 已到且未催过（deliverContent 为空）；</li>
 *   <li>拉账号 VirtualShipConfig，取 confirmReceiptMessage 模板（null 走默认话术）；</li>
 *   <li>调 MessageService.sendMessage 发话术给买家；</li>
 *   <li>标 XianyuOrder.autoReceiptAt=null 防重复催 + status 仍 SHIPPED（等买家真确认）。</li>
 * </ol>
 *
 * <p>注意：闲鱼侧 SDK 无专门「确认收货」MTOP API，本服务走消息话术催确认（轻量可行）。
 * 后续若 SDK 暴露 confirmReceipt API，可在第 3 步后追加真正确认调用。</p>
 */
@Service
public class ConfirmReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmReceiptService.class);
    private static final String JOB_TYPE = "confirm_receipt";
    private static final String DEFAULT_MSG =
            "买家您好，方便确认下收货吗？有助于我店铺评分提升，谢谢~";

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final VirtualShipConfigMapper configMapper;
    private final MessageService messageService;
    private final BatchJobService batchJobService;

    public ConfirmReceiptService(OrderMapper orderMapper,
                                 AccountMapper accountMapper,
                                 VirtualShipConfigMapper configMapper,
                                 MessageService messageService,
                                 BatchJobService batchJobService) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.configMapper = configMapper;
        this.messageService = messageService;
        this.batchJobService = batchJobService;
    }

    /** 执行一次催确认收货批次。由 ConfirmReceiptTask 定时调用，也可管理端手动触发。 */
    public Long runBatch(String triggerSource) {
        // 扫已发货且 autoReceiptAt 已到的订单
        List<XianyuOrder> candidates = orderMapper.selectList(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getStatus, "SHIPPED")
                .isNotNull(XianyuOrder::getAutoReceiptAt)
                .le(XianyuOrder::getAutoReceiptAt, LocalDateTime.now()));
        var job = batchJobService.startBatch(JOB_TYPE, triggerSource, triggerSource, candidates.size());
        int success = 0, failed = 0, skipped = 0;
        for (XianyuOrder order : candidates) {
            long t0 = System.currentTimeMillis();
            try {
                RenewResult r = sendReceiptMessage(order);
                switch (r) {
                    case SUCCESS -> {
                        success++;
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "SUCCESS", System.currentTimeMillis() - t0, null, null);
                    }
                    case SKIPPED -> {
                        skipped++;
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "SKIPPED", System.currentTimeMillis() - t0, "skip: " + r.name(), null);
                    }
                    case FAILED -> {
                        failed++;
                        batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                                Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                                "FAILED", System.currentTimeMillis() - t0, "send receipt msg failed", null);
                    }
                }
            } catch (Exception e) {
                failed++;
                batchJobService.recordItem(job.getId(), String.valueOf(order.getOrderId()),
                        Optional.ofNullable(order.getCounterpartyName()).orElse("buyer#" + order.getBuyerId()),
                        "FAILED", System.currentTimeMillis() - t0, e.getMessage(), null);
            }
        }
        boolean partial = failed > 0 || skipped > 0;
        batchJobService.endBatch(job.getId(), partial, success == 0 && failed > 0,
                String.format("total=%d success=%d failed=%d skipped=%d", candidates.size(), success, failed, skipped));
        return job.getId();
    }

    /** 对单订单发催确认收货话术。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewResult sendReceiptMessage(XianyuOrder order) {
        XianyuAccount account = accountMapper.selectById(order.getAccountId());
        if (account == null || "DISABLED".equals(account.getStatus())) {
            return RenewResult.SKIPPED;
        }
        VirtualShipConfig config = configMapper.selectOne(new LambdaQueryWrapper<VirtualShipConfig>()
                .eq(VirtualShipConfig::getAccountId, order.getAccountId())
                .eq(VirtualShipConfig::getEnabled, true)
                .last("LIMIT 1"));
        String msg = (config != null && config.getConfirmReceiptMessage() != null
                && !config.getConfirmReceiptMessage().isBlank())
                ? config.getConfirmReceiptMessage() : DEFAULT_MSG;
        try {
            MessageSendRequest req = new MessageSendRequest();
            req.setAccountId(account.getId());
            req.setSessionId(null);
            req.setContent(msg);
            messageService.sendMessage(req);
            // 标防重复催：清 autoReceiptAt
            order.setAutoReceiptAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            return RenewResult.SUCCESS;
        } catch (Exception e) {
            log.warn("[A10] order {} send receipt msg failed: {}", order.getOrderId(), e.getMessage());
            return RenewResult.FAILED;
        }
    }

    private enum RenewResult { SUCCESS, SKIPPED, FAILED }
}
