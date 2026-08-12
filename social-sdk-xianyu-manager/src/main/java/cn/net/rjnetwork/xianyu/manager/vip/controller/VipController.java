package cn.net.rjnetwork.xianyu.manager.vip.controller;

import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.vip.dto.VipEmailCodeRequest;
import cn.net.rjnetwork.xianyu.manager.vip.service.VipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VipController {

    private final VipService vipService;
    private final AuthService authService;

    public VipController(VipService vipService, AuthService authService) {
        this.vipService = vipService;
        this.authService = authService;
    }

    @GetMapping("/vip/header-status")
    public ApiResponse<Map<String, Object>> headerStatus(Authentication authentication) {
        return ApiResponse.ok(vipService.headerStatus(currentUser(authentication)));
    }

    @GetMapping("/vip/status")
    public ApiResponse<Map<String, Object>> status(Authentication authentication) {
        return ApiResponse.ok(vipService.status(currentUser(authentication)));
    }

    @PostMapping("/vip/verify")
    public ApiResponse<Map<String, Object>> verify(Authentication authentication) {
        return ApiResponse.ok(vipService.verify(currentUser(authentication)));
    }

    @PostMapping("/vip/community/bind")
    public ApiResponse<Map<String, Object>> bind(Authentication authentication) {
        return ApiResponse.ok(vipService.bind(currentUser(authentication)));
    }

    @GetMapping("/vip/identity")
    public ApiResponse<Map<String, Object>> identity(Authentication authentication) {
        return ApiResponse.ok(vipService.identity(currentUser(authentication)));
    }

    @PostMapping("/vip/email/send-code")
    public ApiResponse<Map<String, Object>> sendEmailCode(Authentication authentication, @RequestBody VipEmailCodeRequest request) {
        return ApiResponse.ok(vipService.sendEmailCode(currentUser(authentication), request));
    }

    @PostMapping("/vip/email/verify")
    public ApiResponse<Map<String, Object>> verifyEmail(Authentication authentication, @RequestBody VipEmailCodeRequest request) {
        return ApiResponse.ok(vipService.verifyEmail(currentUser(authentication), request));
    }

    @GetMapping("/community/menu")
    public ApiResponse<Map<String, Object>> communityMenu(Authentication authentication) {
        return ApiResponse.ok(vipService.communityMenu(currentUser(authentication)));
    }

    /** 查询当前部署接入密钥配置（B 端，不返回 secret） */
    @GetMapping("/vip/access/config")
    public ApiResponse<Map<String, Object>> accessConfig(Authentication authentication) {
        return ApiResponse.ok(vipService.accessConfig(currentUser(authentication)));
    }

    /** 保存接入密钥（B 端：付费后填写 app-id/secret，持久化并动态生效） */
    @PostMapping("/vip/access/config")
    public ApiResponse<Map<String, Object>> saveAccessConfig(Authentication authentication, @RequestBody Map<String, String> body) {
        try {
            return ApiResponse.ok(vipService.saveAccessConfig(currentUser(authentication), body.get("appId"), body.get("secret")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        }
    }

    @RequestMapping(value = "/community/client/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ApiResponse<Object> communityClientProxy(Authentication authentication, HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        String prefix = "/api/community/client";
        String uri = request.getRequestURI();
        String path = uri.length() > prefix.length() ? uri.substring(prefix.length()) : "/topics";
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            path += "?" + request.getQueryString();
        }
        String rawBody = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return ApiResponse.ok(vipService.communityClientProxy(currentUser(authentication), request.getMethod(), path, rawBody));
    }

    private AdminUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return authService.findByUsername(authentication.getName()).orElse(null);
    }
}
