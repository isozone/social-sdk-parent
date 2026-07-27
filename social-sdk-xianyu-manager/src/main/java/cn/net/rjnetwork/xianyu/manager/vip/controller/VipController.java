package cn.net.rjnetwork.xianyu.manager.vip.controller;

import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.vip.dto.VipCreateOrderRequest;
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

    @GetMapping("/vip/config")
    public ApiResponse<Map<String, Object>> config(Authentication authentication) {
        return ApiResponse.ok(vipService.config(currentUser(authentication)));
    }

    @PostMapping("/vip/community/bind")
    public ApiResponse<Map<String, Object>> bind(Authentication authentication) {
        return ApiResponse.ok(vipService.bind(currentUser(authentication)));
    }

    @PostMapping("/vip/orders")
    public ApiResponse<Map<String, Object>> createOrder(Authentication authentication, @RequestBody VipCreateOrderRequest request) {
        return ApiResponse.ok(vipService.createOrder(currentUser(authentication), request));
    }

    @GetMapping("/vip/orders/{localOrderNo}")
    public ApiResponse<Map<String, Object>> orderDetail(Authentication authentication, @PathVariable String localOrderNo) {
        return ApiResponse.ok(vipService.orderDetail(currentUser(authentication), localOrderNo));
    }

    @GetMapping("/community/menu")
    public ApiResponse<Map<String, Object>> communityMenu(Authentication authentication) {
        return ApiResponse.ok(vipService.communityMenu(currentUser(authentication)));
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
