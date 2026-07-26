package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.audit.service.AuditService;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息自动回复 → 站内/站外通知事件闭环验证。
 */
@ExtendWith(MockitoExtension.class)
class MessageAutoReplyNotifyLoopTest {

    @Mock private MessageMapper messageMapper;
    @Mock private RuleService ruleService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountMapper accountMapper;
    @Mock private XianyuCaptchaSolver captchaSolver;
    @Mock private AuditService auditService;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(messageMapper, ruleService, eventPublisher, accountMapper, captchaSolver, auditService);
    }

    @Test
    void autoReplyIfNeeded_sendSuccess_publishesAutoReplySent() throws Exception {
        XianyuAccount acc = new XianyuAccount();
        acc.setId(3L);
        acc.setAccountName("卖家A");
        acc.setDisplayName("卖家A");
        acc.setCookieHeader("unb=1001; cookie2=x");
        when(accountMapper.selectById(3L)).thenReturn(acc);
        when(ruleService.autoReply(eq(3L), eq("在吗"))).thenReturn("在的，亲");

        // 用 spy 拦截真实 sendMessage，避免走闲鱼 IM。
        MessageService spy = spy(service);
        doReturn(null).when(spy).sendMessage(any(MessageSendRequest.class));

        XianyuMessage incoming = new XianyuMessage();
        incoming.setAccountId(3L);
        incoming.setSessionId("2002@goofish");
        incoming.setSenderId("2002@goofish");
        incoming.setContent("在吗");
        incoming.setDirection("INCOMING");
        incoming.setMessageTime(LocalDateTime.now());

        String reply = spy.autoReplyIfNeeded(3L, incoming);
        assertEquals("在的，亲", reply);

        ArgumentCaptor<NotifyEvent> cap = ArgumentCaptor.forClass(NotifyEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        NotifyEvent evt = cap.getValue();
        assertEquals("AUTO_REPLY_SENT", evt.getScenario());
        assertEquals(3L, evt.getAccountId());
        assertEquals("卖家A", evt.getVars().get("accountName"));
        assertEquals("2002@goofish", evt.getVars().get("sessionId"));
        assertEquals("在的，亲", evt.getVars().get("content"));

        ArgumentCaptor<MessageSendRequest> sendCap = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(spy).sendMessage(sendCap.capture());
        assertEquals(3L, sendCap.getValue().getAccountId());
        assertEquals("2002", sendCap.getValue().getBuyerId());
        assertEquals(Boolean.TRUE, sendCap.getValue().getAutoReply());
    }

    @Test
    void autoReplyIfNeeded_sendFailed_publishesAutoReplyFailedAndReturnsNull() throws Exception {
        XianyuAccount acc = new XianyuAccount();
        acc.setId(4L);
        acc.setAccountName("卖家B");
        acc.setDisplayName("卖家B");
        when(accountMapper.selectById(4L)).thenReturn(acc);
        when(ruleService.autoReply(eq(4L), eq("多少钱"))).thenReturn("9.9包邮");

        MessageService spy = spy(service);
        doThrow(new IllegalStateException("风控拦截")).when(spy).sendMessage(any(MessageSendRequest.class));

        XianyuMessage incoming = new XianyuMessage();
        incoming.setAccountId(4L);
        incoming.setSessionId("3003@goofish");
        incoming.setSenderId("3003");
        incoming.setContent("多少钱");
        incoming.setDirection("INCOMING");

        String reply = spy.autoReplyIfNeeded(4L, incoming);
        assertNull(reply);

        ArgumentCaptor<NotifyEvent> cap = ArgumentCaptor.forClass(NotifyEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        NotifyEvent evt = cap.getValue();
        assertEquals("AUTO_REPLY_FAILED", evt.getScenario());
        assertEquals(4L, evt.getAccountId());
        assertTrue(String.valueOf(evt.getVars().get("reason")).contains("风控拦截"));
        assertEquals("多少钱", evt.getVars().get("content"));
    }
}
