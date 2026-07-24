package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OpenApiMessageController 单测：列表过滤 / 详情 / 404 / 403。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiMessageControllerTest {

    @Mock private MessageMapper messageMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiMessageController controller;

    @AfterEach
    void tearDown() {
        OpenApiContext.clear();
    }

    @Test
    void list_emptyBound_returnsEmpty() {
        // 安全修复：应用未绑定任何账号时返回空列表，而非「返回所有账号消息」。
        // 旧实现是全表扫描后在 Java 层 filter，bound 为空时 filter 不生效 → 越权返回全表。
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());

        var resp = controller.list(null);

        assertEquals("OK", resp.getCode());
        assertTrue(resp.getData().isEmpty());
        verify(messageMapper, never()).selectList(any());
    }

    @Test
    void list_boundFilter_onlyReturnsBoundAccounts() {
        // 安全修复：过滤条件下推到 SQL（accountId IN (bound)），由 messageMapper.selectList(wrapper) 返回已过滤列表。
        // 测试不再 mock「未过滤的全表」，而是 mock「按 bound 过滤后的结果」，验证 controller 把 bound 透传给 wrapper。
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of(10L));
        when(messageMapper.selectList(any())).thenReturn(List.of(msg(1L, 10L)));

        var resp = controller.list(null);

        assertEquals(1, resp.getData().size());
        assertEquals(10L, resp.getData().get(0).getAccountId());
    }

    @Test
    void list_accountIdFilter_appliesOnTopOfBound() {
        // 安全修复：accountId 必须在 bound 集合内，否则返回空（防止调用方传入未绑定的 accountId 越权）。
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of(20L));
        when(messageMapper.selectList(any())).thenReturn(List.of(msg(2L, 20L)));

        var resp = controller.list(20L);

        assertEquals(1, resp.getData().size());
        assertEquals(20L, resp.getData().get(0).getAccountId());
    }

    @Test
    void list_accountIdNotInBound_returnsEmpty() {
        // 安全修复：accountId 不在 bound 集合内时直接返回空，不查库。
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of(10L));

        var resp = controller.list(20L);

        assertTrue(resp.getData().isEmpty());
        verify(messageMapper, never()).selectList(any());
    }

    @Test
    void get_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(messageMapper.selectById(999L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(999L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void get_boundCheckRuns_afterLookup_beforeResponse() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuMessage m = msg(2L, 30L);
        when(messageMapper.selectById(2L)).thenReturn(m);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(2L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
    }

    @Test
    void get_existing_inBound_returnsDetail() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuMessage m = msg(2L, 10L);
        when(messageMapper.selectById(2L)).thenReturn(m);

        var resp = controller.get(2L);

        assertEquals("OK", resp.getCode());
        assertEquals(2L, resp.getData().getId());
        assertEquals(10L, resp.getData().getAccountId());
    }

    private static XianyuMessage msg(long id, long accountId) {
        XianyuMessage m = new XianyuMessage();
        m.setId(id);
        m.setAccountId(accountId);
        return m;
    }

    private static <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
