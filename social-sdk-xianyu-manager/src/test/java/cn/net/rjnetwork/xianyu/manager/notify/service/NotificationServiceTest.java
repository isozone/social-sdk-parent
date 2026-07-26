package cn.net.rjnetwork.xianyu.manager.notify.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.message.websocket.MessageBroadcaster;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.notify.adapter.ChannelAdapter;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyChannelMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyLogMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyMessageMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifySubscriptionMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyTemplateMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyLog;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyMessage;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifySubscription;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 通知编排闭环单测：
 * NotifyEvent → 站内收件箱 + 站内广播 + 站外通道发送 + 投递日志。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotifyChannelMapper channelMapper;
    @Mock private NotifyTemplateMapper templateMapper;
    @Mock private NotifySubscriptionMapper subscriptionMapper;
    @Mock private NotifyLogMapper logMapper;
    @Mock private NotifyMessageMapper messageMapper;
    @Mock private CryptoUtil cryptoUtil;
    @Mock private MessageBroadcaster broadcaster;
    @Mock private SendRateLimiter rateLimiter;
    @Mock private RetryService retryService;

    private RecordingWebhookAdapter webhookAdapter;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        webhookAdapter = new RecordingWebhookAdapter();
        service = new NotificationService(
                channelMapper,
                templateMapper,
                subscriptionMapper,
                logMapper,
                messageMapper,
                cryptoUtil,
                broadcaster,
                List.of(webhookAdapter),
                rateLimiter,
                retryService
        );
    }

    @Test
    void onEvent_virtualShipSuccess_writesInAppAndDispatchesExternalChannel() throws Exception {
        NotifyChannel channel = channel(11L, "WEBHOOK", "企微机器人", true, "enc-cfg");
        NotifySubscription sub = subscription(21L, "VIRTUAL_SHIP_SUCCESS", 11L, true, "ALL", "ALL");

        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(subscriptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sub));
        when(channelMapper.selectById(11L)).thenReturn(channel);
        when(cryptoUtil.decrypt("enc-cfg")).thenReturn("{\"url\":\"https://example.test/hook\"}");
        when(rateLimiter.tryAcquire(eq(11L), anyInt())).thenReturn(true);

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("accountName", "测试账号");
        vars.put("orderId", "ORD-1001");
        vars.put("buyerName", "买家A");
        vars.put("itemTitle", "虚拟卡密商品");

        service.onEvent(new NotifyEvent("VIRTUAL_SHIP_SUCCESS", 7L, "测试账号", vars));

        ArgumentCaptor<NotifyMessage> inAppCap = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(messageMapper).insert(inAppCap.capture());
        NotifyMessage inApp = inAppCap.getValue();
        assertEquals(7L, inApp.getAccountId());
        assertEquals("VIRTUAL_SHIP_SUCCESS", inApp.getScenario());
        assertTrue(inApp.getTitle().contains("ORD-1001"));
        assertTrue(inApp.getContent().contains("测试账号"));
        assertTrue(inApp.getContent().contains("买家A"));
        assertEquals(Boolean.FALSE, inApp.getIsRead());

        verify(broadcaster).broadcastAll(argThat(json ->
                json.contains("\"type\":\"notification\"")
                        && json.contains("VIRTUAL_SHIP_SUCCESS")
                        && json.contains("ORD-1001")));

        assertNotNull(webhookAdapter.lastTitle.get());
        assertTrue(webhookAdapter.lastTitle.get().contains("ORD-1001"));
        assertTrue(webhookAdapter.lastBody.get().contains("无需物流发货确认"));
        assertEquals("WEBHOOK", webhookAdapter.lastChannel.get().getType());
        assertEquals("{\"url\":\"https://example.test/hook\"}", webhookAdapter.lastChannel.get().getConfigJson());

        ArgumentCaptor<NotifyLog> logCap = ArgumentCaptor.forClass(NotifyLog.class);
        verify(logMapper).insert(logCap.capture());
        NotifyLog log = logCap.getValue();
        assertEquals("VIRTUAL_SHIP_SUCCESS", log.getScenario());
        assertEquals(11L, log.getChannelId());
        assertEquals("WEBHOOK", log.getChannelType());
        assertEquals("SENT", log.getStatus());
        assertNotNull(log.getSentAt());
        verify(retryService, never()).enqueue(any(), any(), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void onEvent_noSubscription_onlyInAppNotification() throws Exception {
        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(subscriptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("accountName", "账号B");
        vars.put("sessionId", "123@goofish");
        vars.put("content", "自动回复内容");

        service.onEvent(new NotifyEvent("AUTO_REPLY_SENT", 9L, "账号B", vars));

        verify(messageMapper).insert(argThat(msg ->
                "AUTO_REPLY_SENT".equals(msg.getScenario())
                        && Long.valueOf(9L).equals(msg.getAccountId())
                        && msg.getTitle() != null
                        && msg.getTitle().contains("账号B")));
        verify(broadcaster).broadcastAll(anyString());
        verify(channelMapper, never()).selectById(any());
        verify(logMapper, never()).insert(any());
        assertNull(webhookAdapter.lastTitle.get());
    }

    @Test
    void onEvent_externalSendFailed_writesFailedLogAndEnqueuesRetry() throws Exception {
        NotifyChannel channel = channel(12L, "WEBHOOK", "失败通道", true, "enc-cfg-2");
        NotifySubscription sub = subscription(22L, "VIRTUAL_SHIP_FAILED", 12L, true, "ALL", "ALL");
        webhookAdapter.fail = true;

        when(templateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(subscriptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sub));
        when(channelMapper.selectById(12L)).thenReturn(channel);
        when(cryptoUtil.decrypt("enc-cfg-2")).thenReturn("{\"url\":\"https://example.test/fail\"}");
        when(rateLimiter.tryAcquire(eq(12L), anyInt())).thenReturn(true);

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("accountName", "账号C");
        vars.put("orderId", "ORD-FAIL");
        vars.put("itemTitle", "商品C");
        vars.put("reason", "dummyDelivery failed");

        service.onEvent(new NotifyEvent("VIRTUAL_SHIP_FAILED", 3L, "账号C", vars));

        verify(messageMapper).insert(any(NotifyMessage.class));
        ArgumentCaptor<NotifyLog> logCap = ArgumentCaptor.forClass(NotifyLog.class);
        verify(logMapper).insert(logCap.capture());
        assertEquals("FAILED", logCap.getValue().getStatus());
        assertTrue(logCap.getValue().getError().contains("boom"));
        verify(retryService).enqueue(any(NotifyEvent.class), eq(channel), anyList(), anyString(), anyString(), contains("boom"));
    }

    private static NotifyChannel channel(Long id, String type, String name, boolean enabled, String cfg) {
        NotifyChannel c = new NotifyChannel();
        c.setId(id);
        c.setType(type);
        c.setName(name);
        c.setEnabled(enabled);
        c.setConfigJson(cfg);
        return c;
    }

    private static NotifySubscription subscription(Long id, String scenario, Long channelId,
                                                   boolean enabled, String accountScope, String recipientScope) {
        NotifySubscription s = new NotifySubscription();
        s.setId(id);
        s.setScenario(scenario);
        s.setChannelId(channelId);
        s.setEnabled(enabled);
        s.setAccountScope(accountScope);
        s.setRecipientScope(recipientScope);
        return s;
    }

    /** 记录站外发送调用，用于断言闭环真实到达适配器。 */
    static class RecordingWebhookAdapter implements ChannelAdapter {
        final AtomicReference<NotifyChannel> lastChannel = new AtomicReference<>();
        final AtomicReference<String> lastTitle = new AtomicReference<>();
        final AtomicReference<String> lastBody = new AtomicReference<>();
        boolean fail;

        @Override
        public String type() {
            return "WEBHOOK";
        }

        @Override
        public void send(NotifyChannel channel, String title, String body,
                         List<String> recipients, Map<String, Object> vars) throws Exception {
            lastChannel.set(channel);
            lastTitle.set(title);
            lastBody.set(body);
            if (fail) {
                throw new IllegalStateException("boom");
            }
        }
    }
}
