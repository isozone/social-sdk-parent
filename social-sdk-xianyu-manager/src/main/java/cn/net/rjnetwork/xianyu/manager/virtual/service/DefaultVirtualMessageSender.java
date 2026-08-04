package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 虚拟发货消息发送器 — 真实走 MessageService.sendMessage。
 * <p>当前订单表未存 sessionId，统一用 buyerId 生成闲鱼会话 cid。</p>
 */
@Component
public class DefaultVirtualMessageSender implements VirtualMessageSender {

    private static final Logger log = LoggerFactory.getLogger(DefaultVirtualMessageSender.class);

    private final MessageService messageService;

    public DefaultVirtualMessageSender(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public boolean sendToBuyer(XianyuOrder order, String content) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (order.getAccountId() == null) {
            throw new IllegalStateException("order.accountId is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("deliver content is blank");
        }
        String buyerId = stripSuffix(order.getBuyerId());
        if (buyerId.isBlank()) {
            throw new IllegalStateException("order.buyerId is blank, cannot send virtual ship message");
        }

        MessageSendRequest req = new MessageSendRequest();
        req.setAccountId(order.getAccountId());
        req.setBuyerId(buyerId);
        // 不传 normalizeCid(buyerId) 假会话：传闲鱼订单号，由 MessageService 反查订单会话真实会话
        req.setOrderId(order.getOrderId());
        req.setContent(content);
        req.setAutoReply(false);
        try {
            messageService.sendMessage(req);
            log.info("[VirtualShip] real message sent orderId={} accountId={} buyerId={}",
                    order.getOrderId(), order.getAccountId(), buyerId);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("发送发货消息失败: " + e.getMessage(), e);
        }
    }

    private static String stripSuffix(String userId) {
        if (userId == null) return "";
        String trimmed = userId.trim();
        int at = trimmed.indexOf('@');
        return at > 0 ? trimmed.substring(0, at) : trimmed;
    }

    private static String normalizeCid(String userId) {
        String bare = stripSuffix(userId);
        if (bare.isBlank()) return "";
        return bare.contains("@") ? bare : bare + "@goofish";
    }
}
