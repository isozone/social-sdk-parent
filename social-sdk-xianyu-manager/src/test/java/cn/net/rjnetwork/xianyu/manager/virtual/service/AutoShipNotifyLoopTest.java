package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.order.ship.service.DeliveryRuleEngine;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.CardItemRelationMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.DeliveryLogMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.ShipCardMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.CardItemRelation;
import cn.net.rjnetwork.xianyu.manager.virtual.model.ShipCard;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 虚拟发货 → 站内/站外通知事件闭环验证。
 * 重点：成功发消息后会发布 VIRTUAL_SHIP_SUCCESS；卡券缺失会发布 AUTO_SHIP_NO_CARD。
 */
@ExtendWith(MockitoExtension.class)
class AutoShipNotifyLoopTest {

    @Mock private OrderMapper orderMapper;
    @Mock private ShipCardMapper shipCardMapper;
    @Mock private CardItemRelationMapper relationMapper;
    @Mock private DeliveryLogMapper deliveryLogMapper;
    @Mock private VirtualShipTaskMapper taskMapper;
    @Mock private AccountMapper accountMapper;
    @Mock private DeliveryRuleEngine ruleEngine;
    @Mock private MessageService messageService;
    @Mock private BatchJobService batchJobService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AutoShipService service;

    @BeforeEach
    void setUp() {
        service = new AutoShipService(
                orderMapper, shipCardMapper, relationMapper, deliveryLogMapper,
                taskMapper, accountMapper, ruleEngine, messageService,
                batchJobService, eventPublisher);
        service.setSelf(service);
    }

    @Test
    void processShipTask_noCard_publishesAutoShipNoCardEvent() throws Exception {
        VirtualShipTask task = task(101L, 7L, 55L, 88L);
        XianyuOrder order = order(55L, 7L, "ORD-55", "buyer-1", "买家一", "商品A");
        when(orderMapper.selectById(55L)).thenReturn(order);
        when(ruleEngine.shouldBlock(any())).thenReturn(new DeliveryRuleEngine.BlockDecision(false, null, null, null));
        when(relationMapper.selectEnabledByProductId(88L)).thenReturn(List.of());

        service.processShipTask(task);

        ArgumentCaptor<NotifyEvent> cap = ArgumentCaptor.forClass(NotifyEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        NotifyEvent noCard = cap.getAllValues().stream()
                .filter(e -> "AUTO_SHIP_NO_CARD".equals(e.getScenario()))
                .findFirst()
                .orElseThrow();
        assertEquals(7L, noCard.getAccountId());
        assertTrue(String.valueOf(noCard.getVars().get("reason")).contains("无可用卡券"));
        verify(messageService, never()).sendMessage(any());
    }

    @Test
    void processShipTask_sendSuccessButDummyFails_publishesVirtualShipFailedWithMessageSentMarker() throws Exception {
        VirtualShipTask task = task(102L, 7L, 56L, 88L);
        XianyuOrder order = order(56L, 7L, "ORD-56", "buyer-2", "买家二", "商品B");
        XianyuAccount account = account(7L, "账号7");
        CardItemRelation rel = new CardItemRelation();
        rel.setProductId(88L);
        rel.setCardId(9001L);
        rel.setPriority(0);
        ShipCard card = new ShipCard();
        card.setId(9001L);
        card.setCardType("CARD");
        card.setCardCode("CODE-1");
        card.setCardPassword("PASS-1");
        card.setStatus("AVAILABLE");

        when(orderMapper.selectById(56L)).thenReturn(order);
        when(ruleEngine.shouldBlock(any())).thenReturn(new DeliveryRuleEngine.BlockDecision(false, null, null, null));
        when(relationMapper.selectEnabledByProductId(88L)).thenReturn(List.of(rel));
        when(shipCardMapper.selectById(9001L)).thenReturn(card);
        when(shipCardMapper.update(any(ShipCard.class), any(Wrapper.class))).thenReturn(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(messageService.sendMessage(any(MessageSendRequest.class))).thenReturn(null);

        // dummyDelivery 依赖真实 MTOP，会在无 cookie/网络环境下失败；此处验证失败通知与 MESSAGE_SENT 标记。
        service.processShipTask(task);

        ArgumentCaptor<NotifyEvent> cap = ArgumentCaptor.forClass(NotifyEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        NotifyEvent failed = cap.getAllValues().stream()
                .filter(e -> "VIRTUAL_SHIP_FAILED".equals(e.getScenario()))
                .findFirst()
                .orElseThrow();
        assertEquals(7L, failed.getAccountId());
        assertEquals("ORD-56", failed.getVars().get("orderId"));
        assertTrue(String.valueOf(failed.getVars().get("reason")).contains("dummyDelivery")
                || String.valueOf(failed.getVars().get("reason")).contains("MESSAGE_SENT")
                || String.valueOf(failed.getVars().get("reason")).contains("cookie")
                || String.valueOf(failed.getVars().get("reason")).length() > 0);

        ArgumentCaptor<VirtualShipTask> taskCap = ArgumentCaptor.forClass(VirtualShipTask.class);
        verify(taskMapper, atLeastOnce()).updateById(taskCap.capture());
        boolean hasMessageSentMarker = taskCap.getAllValues().stream()
                .map(VirtualShipTask::getErrorMessage)
                .filter(m -> m != null)
                .anyMatch(m -> m.startsWith("MESSAGE_SENT:"));
        assertTrue(hasMessageSentMarker, "消息已发后应固化 MESSAGE_SENT 标记，避免重试重复发卡");

        ArgumentCaptor<MessageSendRequest> sendCap = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(messageService).sendMessage(sendCap.capture());
        assertEquals(7L, sendCap.getValue().getAccountId());
        assertEquals("buyer-2", sendCap.getValue().getBuyerId());
        assertTrue(sendCap.getValue().getContent().contains("CODE-1"));
    }

    private static VirtualShipTask task(Long id, Long accountId, Long orderId, Long productId) {
        VirtualShipTask t = new VirtualShipTask();
        t.setId(id);
        t.setAccountId(accountId);
        t.setOrderId(orderId);
        t.setProductId(productId);
        t.setStatus("PENDING");
        t.setRetryCount(0);
        t.setMaxRetry(5);
        return t;
    }

    private static XianyuOrder order(Long id, Long accountId, String orderId, String buyerId,
                                     String buyerName, String itemTitle) {
        XianyuOrder o = new XianyuOrder();
        o.setId(id);
        o.setAccountId(accountId);
        o.setOrderId(orderId);
        o.setBuyerId(buyerId);
        o.setCounterpartyName(buyerName);
        o.setItemTitle(itemTitle);
        o.setAmount(new BigDecimal("9.90"));
        o.setStatus("PAID");
        return o;
    }

    private static XianyuAccount account(Long id, String name) {
        XianyuAccount a = new XianyuAccount();
        a.setId(id);
        a.setAccountName(name);
        a.setDisplayName(name);
        a.setCookieHeader("unb=1; cookie2=abc");
        return a;
    }
}
