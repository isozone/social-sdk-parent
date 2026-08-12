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

    /** 查询当前部署接入密钥配置（B 端，只读，不返回 secret） */
    @GetMapping("/vip/access/config")
    public ApiResponse<Map<String, Object>> accessConfig(Authentication authentication) {
        return ApiResponse.ok(vipService.accessConfig(currentUser(authentication)));
    }

    /** 拉取接入密钥套餐（app_access，由 new-api 托管，替代手填 app-id/secret） */
    @GetMapping("/vip/access/plans")
    public ApiResponse<Map<String, Object>> accessPlans(Authentication authentication) {
        return ApiResponse.ok(vipService.accessPlans(currentUser(authentication)));
    }

    /** 创建接入密钥订单并发起支付（new-api 托管，付费后自动生成部署专属密钥） */
    @PostMapping("/vip/access/apply")
    public ApiResponse<Map<String, Object>> accessApply(Authentication authentication, @RequestBody Map<String, Object> body) {
        try {
            Integer planId = body.get("planId") != null ? Integer.valueOf(String.valueOf(body.get("planId"))) : null;
            String channel = body.get("channel") != null ? String.valueOf(body.get("channel")) : "";
            String returnUrl = body.get("returnUrl") != null ? String.valueOf(body.get("returnUrl")) : "";
            return ApiResponse.ok(vipService.accessApply(currentUser(authentication), planId, channel, returnUrl));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        }
    }

    /** 凭订单号获取已付费生成的接入密钥并落地（new-api 托管；订单未支付时返回错误，前端继续轮询） */
    @GetMapping("/vip/access/credential")
    public ApiResponse<Map<String, Object>> accessCredential(Authentication authentication, @RequestParam("orderNo") String orderNo) {
        try {
            return ApiResponse.ok(vipService.accessCredential(currentUser(authentication), orderNo));
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
