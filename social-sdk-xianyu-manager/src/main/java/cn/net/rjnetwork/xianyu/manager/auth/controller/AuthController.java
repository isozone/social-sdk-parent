package cn.net.rjnetwork.xianyu.manager.auth.controller;

import cn.net.rjnetwork.xianyu.manager.auth.dto.ChangePasswordRequest;
import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse;
import cn.net.rjnetwork.xianyu.manager.auth.dto.LoginRequest;
import cn.net.rjnetwork.xianyu.manager.auth.dto.UpdateProfileRequest;
import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }

    /**
     * 从 Spring Security Authentication 安全获取当前用户信息，不再自行解析 Bearer token。
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(Authentication authentication) {
        String username = requireUsername(authentication);
        AdminUser user = authService.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.fail("USER_NOT_FOUND", "User not found");
        }
        return ApiResponse.ok(toProfileMap(user));
    }

    /**
     * 更新当前管理员个人信息（昵称 / 邮箱 / 手机号）。
     */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        String username = requireUsername(authentication);
        AdminUser user = authService.updateProfile(username, request);
        return ApiResponse.ok(toProfileMap(user));
    }

    /**
     * 修改当前管理员登录密码。
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        String username = requireUsername(authentication);
        authService.changePassword(username, request);
        return ApiResponse.ok(null);
    }

    /** 从 Authentication 解析当前用户名，未认证时返回错误。 */
    private String requireUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalArgumentException("请先登录");
        }
        return authentication.getName();
    }

    /**
     * Map.of 不允许 null value，admin 未填 email/phone/displayName 时会直接 NPE 成 500。
     * 统一转成空串，保证 GET/PUT /profile 返回结构稳定。
     */
    private Map<String, Object> toProfileMap(AdminUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
        data.put("email", user.getEmail() != null ? user.getEmail() : "");
        data.put("phone", user.getPhone() != null ? user.getPhone() : "");
        data.put("roleLevel", user.getRoleLevel() != null ? user.getRoleLevel() : 0);
        return data;
    }
}
