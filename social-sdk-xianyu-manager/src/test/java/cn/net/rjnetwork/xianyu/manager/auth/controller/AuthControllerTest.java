package cn.net.rjnetwork.xianyu.manager.auth.controller;

import cn.net.rjnetwork.xianyu.manager.auth.dto.UpdateProfileRequest;
import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AuthController 单测：重点覆盖 email/phone/displayName 为 null 时
 * 不得因 Map.of 触发 NPE，返回结构必须稳定。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @Test
    void getProfile_nullOptionalFields_returnsEmptyStrings() {
        AdminUser user = admin(1L, "admin", null, null, null, 9);
        when(authService.findByUsername("admin")).thenReturn(Optional.of(user));

        ApiResponse<Map<String, Object>> resp = controller.getProfile(auth("admin"));

        assertTrue(resp.isSuccess());
        assertEquals("OK", resp.getCode());
        assertEquals(1L, resp.getData().get("id"));
        assertEquals("admin", resp.getData().get("username"));
        assertEquals("", resp.getData().get("displayName"));
        assertEquals("", resp.getData().get("email"));
        assertEquals("", resp.getData().get("phone"));
        assertEquals(9, resp.getData().get("roleLevel"));
    }

    @Test
    void updateProfile_nullOptionalFields_returnsEmptyStrings() {
        AdminUser user = admin(1L, "admin", null, null, null, 9);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("管理员");
        when(authService.updateProfile(eq("admin"), eq(req))).thenReturn(user);

        ApiResponse<Map<String, Object>> resp = controller.updateProfile(auth("admin"), req);

        assertTrue(resp.isSuccess());
        assertEquals("", resp.getData().get("email"));
        assertEquals("", resp.getData().get("phone"));
        assertEquals("", resp.getData().get("displayName"));
    }

    @Test
    void getProfile_unauthenticated_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.getProfile(null));
        assertEquals("请先登录", ex.getMessage());
    }

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a");
    }

    private static AdminUser admin(Long id, String username, String displayName,
                                   String email, String phone, Integer roleLevel) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRoleLevel(roleLevel);
        return user;
    }
}
