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
        if (authentication == null || authentication.getName() == null) {
            return ApiResponse.fail("AUTH_REQUIRED", "Authentication required");
        }

        String username = authentication.getName();
        AdminUser user = authService.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.fail("USER_NOT_FOUND", "User not found");
        }
        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "roleLevel", user.getRoleLevel()
        );
        return ApiResponse.ok(data);
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
        return ApiResponse.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail(),
                "phone", user.getPhone(),
                "roleLevel", user.getRoleLevel()
        ));
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
}
