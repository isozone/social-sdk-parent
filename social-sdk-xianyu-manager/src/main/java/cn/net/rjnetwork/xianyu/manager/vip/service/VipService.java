package cn.net.rjnetwork.xianyu.manager.vip.service;

import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.vip.config.NewApiCommunityProperties;
import cn.net.rjnetwork.xianyu.manager.vip.dto.VipCreateOrderRequest;
import cn.net.rjnetwork.xianyu.manager.vip.mapper.CommunityUserBindingMapper;
import cn.net.rjnetwork.xianyu.manager.vip.mapper.SdkDeploymentMapper;
import cn.net.rjnetwork.xianyu.manager.vip.mapper.VipOrderMapper;
import cn.net.rjnetwork.xianyu.manager.vip.mapper.VipSubscriptionMapper;
import cn.net.rjnetwork.xianyu.manager.vip.model.CommunityUserBinding;
import cn.net.rjnetwork.xianyu.manager.vip.model.SdkDeployment;
import cn.net.rjnetwork.xianyu.manager.vip.model.VipOrder;
import cn.net.rjnetwork.xianyu.manager.vip.model.VipSubscription;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VipService {

    private static final Long DEFAULT_LOCAL_USER_ID = 1L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final NewApiCommunityProperties properties;
    private final SdkDeploymentMapper deploymentMapper;
    private final CommunityUserBindingMapper bindingMapper;
    private final VipSubscriptionMapper subscriptionMapper;
    private final VipOrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VipService(NewApiCommunityProperties properties,
                      SdkDeploymentMapper deploymentMapper,
                      CommunityUserBindingMapper bindingMapper,
                      VipSubscriptionMapper subscriptionMapper,
                      VipOrderMapper orderMapper,
                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.deploymentMapper = deploymentMapper;
        this.bindingMapper = bindingMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .build();
    }

    public Map<String, Object> headerStatus(AdminUser user) {
        Long localUserId = localUserId(user);
        VipSubscription subscription = getSubscription(localUserId);
        CommunityUserBinding binding = getBinding(localUserId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (subscription != null && "ACTIVE".equals(subscription.getStatus()) && subscription.getExpiredAt() != null && subscription.getExpiredAt().isAfter(LocalDateTime.now())) {
            data.put("state", "active");
            data.put("label", "I 社区");
            data.put("communityUid", subscription.getCommunityUid());
            data.put("vipLevel", subscription.getVipLevel());
            data.put("expiredAt", subscription.getExpiredAt());
            data.put("daysLeft", Math.max(0, Duration.between(LocalDateTime.now(), subscription.getExpiredAt()).toDays()));
            return data;
        }
        if (binding != null && binding.getCommunityUid() != null && !binding.getCommunityUid().isBlank()) {
            data.put("state", "expired");
            data.put("label", "I 社区 · 已到期");
            data.put("communityUid", binding.getCommunityUid());
            data.put("vipLevel", subscription != null ? subscription.getVipLevel() : "free");
            data.put("expiredAt", subscription != null ? subscription.getExpiredAt() : null);
            return data;
        }
        VipOrder pending = getLatestPendingOrder(localUserId);
        data.put("state", pending != null ? "pending_payment" : "locked");
        data.put("label", pending != null ? "解锁 VIP · 待支付" : "解锁 VIP");
        data.put("communityUid", binding != null ? binding.getCommunityUid() : "");
        data.put("vipLevel", "free");
        data.put("pendingOrderNo", pending != null ? pending.getLocalOrderNo() : "");
        return data;
    }

    public Map<String, Object> status(AdminUser user) {
        Long localUserId = localUserId(user);
        VipSubscription sub = getSubscription(localUserId);
        CommunityUserBinding binding = getBinding(localUserId);
        Map<String, Object> data = new LinkedHashMap<>();
        boolean active = sub != null && "ACTIVE".equals(sub.getStatus()) && sub.getExpiredAt() != null && sub.getExpiredAt().isAfter(LocalDateTime.now());
        data.put("active", active);
        data.put("communityUid", sub != null ? sub.getCommunityUid() : binding != null ? binding.getCommunityUid() : "");
        data.put("vipLevel", active ? sub.getVipLevel() : "free");
        data.put("expiredAt", sub != null ? sub.getExpiredAt() : null);
        data.put("features", parseJson(sub != null ? sub.getFeaturesJson() : null, List.of()));
        data.put("limits", parseJson(sub != null ? sub.getLimitsJson() : null, Map.of("max_accounts", 1, "max_rules", 3)));
        data.put("lastVerifiedAt", sub != null ? sub.getLastVerifiedAt() : null);
        return data;
    }

    public Map<String, Object> config(AdminUser user) {
        ensureBinding(user);
        try {
            Map<String, Object> config = externalGet("/api/community/external/social-sdk/vip/config");
            Object plans = config.get("plans");
            if (plans instanceof List<?> planList) {
                config.put("plans", planList.stream()
                        .filter(item -> item instanceof Map<?, ?>)
                        .map(item -> (Map<?, ?>) item)
                        .filter(plan -> "social_sdk_vip".equals(stringValue(plan.get("product_type"))) && "social-sdk".equals(stringValue(plan.get("app_code"))))
                        .toList());
            }
            return config;
        } catch (Exception e) {
            throw new IllegalStateException("无法从 I 社区拉取真实 VIP 套餐配置：" + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> bind(AdminUser user) {
        return ensureBinding(user);
    }

    @Transactional
    public Map<String, Object> createOrder(AdminUser user, VipCreateOrderRequest request) {
        if (request == null || request.getPlanId() == null || request.getPlanId() <= 0 || request.getChannel() == null || request.getChannel().isBlank()) {
            throw new IllegalArgumentException("套餐和支付渠道不能为空");
        }
        Long localUserId = localUserId(user);
        String deploymentId = ensureDeployment().getDeploymentId();
        Map<String, Object> bind = ensureBinding(user);
        Long communityUserId = asLong(bind.get("community_user_id"));
        String localOrderNo = "SDKVIP" + System.currentTimeMillis() + randomCode(6);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("community_user_id", communityUserId);
        body.put("deployment_id", deploymentId);
        body.put("plan_id", request.getPlanId());
        body.put("channel", normalizeChannel(request.getChannel()));
        body.put("client_order_no", localOrderNo);
        body.put("client_origin", "");
        body.put("return_url", "");
        Map<String, Object> result;
        try {
            result = externalPost("/api/community/external/social-sdk/vip/orders", body);
        } catch (Exception e) {
            throw new IllegalStateException("无法通过 I 社区创建真实 VIP 支付订单：" + e.getMessage(), e);
        }
        VipOrder order = new VipOrder();
        order.setLocalUserId(localUserId);
        order.setDeploymentId(deploymentId);
        order.setCommunityUserId(communityUserId);
        order.setLocalOrderNo(localOrderNo);
        order.setNewApiOrderNo(stringValue(result.get("order_no")));
        order.setPlanId(String.valueOf(request.getPlanId()));
        order.setPayChannel(normalizeChannel(request.getChannel()));
        order.setPayAmount(BigDecimal.valueOf(asLong(result.getOrDefault("pay_amount", 0L))).movePointLeft(2));
        order.setCurrency("CNY");
        order.setStatus(stringValue(result.getOrDefault("status", "pending")));
        order.setPayInfoJson(toJson(result.get("pay_info")));
        orderMapper.insert(order);
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("local_order_no", localOrderNo);
        return response;
    }

    @Transactional
    public Map<String, Object> orderDetail(AdminUser user, String localOrderNo) {
        Long localUserId = localUserId(user);
        VipOrder order = orderMapper.selectOne(new LambdaQueryWrapper<VipOrder>()
                .eq(VipOrder::getLocalUserId, localUserId)
                .eq(VipOrder::getLocalOrderNo, localOrderNo)
                .last("LIMIT 1"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (order.getNewApiOrderNo() != null && !order.getNewApiOrderNo().isBlank()) {
            try {
                result = externalGet("/api/community/external/social-sdk/vip/orders/" + order.getNewApiOrderNo());
                syncPaidOrder(user, order, result);
            } catch (Exception ignored) {
                result.put("order_no", order.getNewApiOrderNo());
                result.put("status", order.getStatus());
            }
        }
        result.put("local_order_no", order.getLocalOrderNo());
        return result;
    }

    public Map<String, Object> communityMenu(AdminUser user) {
        Map<String, Object> status = headerStatus(user);
        boolean enabled = "active".equals(status.get("state")) || "expired".equals(status.get("state"));
        return Map.of(
                "enabled", enabled,
                "items", List.of(
                        menu("home", "社区首页", "/community/home"),
                        menu("profile", "我的身份", "/community/profile"),
                        menu("benefits", "我的权益", "/community/benefits"),
                        menu("bindings", "账户绑定", "/community/bindings"),
                        menu("topics", "帖子广场", "/community/topics"),
                        menu("composer", "发布帖子", "/community/composer"),
                        menu("wallet", "社区钱包", "/community/wallet"),
                        menu("orders", "支付订单", "/community/orders"),
                        menu("announcements", "社区公告", "/community/announcements"),
                        menu("resources", "资源中心", "/community/resources"),
                        menu("support", "工单支持", "/community/support")
                )
        );
    }

    @SuppressWarnings("unchecked")
    public Object communityClientProxy(AdminUser user, String method, String pathWithQuery, String body) {
        Long localUserId = localUserId(user);
        CommunityUserBinding binding = getBinding(localUserId);
        if (binding == null || binding.getDeploymentId() == null || binding.getBindToken() == null || binding.getBindToken().isBlank()) {
            throw new IllegalStateException("I 社区账户尚未绑定，无法访问社区客户端功能");
        }
        try {
            String path = "/api/community" + normalizeProxyPath(pathWithQuery);
            HttpRequest request = signedCommunityRequest(method, path, body == null ? "" : body, binding).build();
            Map<String, Object> payload = sendRaw(request);
            if (payload.containsKey("data")) {
                return payload.get("data");
            }
            return payload;
        } catch (Exception e) {
            throw new IllegalStateException("访问 I 社区失败：" + e.getMessage(), e);
        }
    }

    private void syncPaidOrder(AdminUser user, VipOrder order, Map<String, Object> result) {
        String status = stringValue(result.get("status"));
        order.setStatus(status);
        Object communityUser = result.get("community_user");
        Object entitlement = result.get("entitlement");
        if ("paid".equalsIgnoreCase(status) && entitlement instanceof Map<?, ?> entitlementMap) {
            Map<?, ?> userMap = communityUser instanceof Map<?, ?> m ? m : Map.of();
            String communityUid = stringValue(userMap.get("community_uid"));
            validateChannelPrefix(communityUid, order.getPayChannel());
            order.setCommunityUid(communityUid);
            order.setEntitlementJson(toJson(entitlementMap));
            Object paidAt = result.get("paid_at");
            if (paidAt != null) {
                order.setPaidAt(fromEpoch(asLong(paidAt)));
            }
            upsertBindingFromPaid(user, order, userMap);
            upsertSubscription(user, order, entitlementMap, communityUid);
        }
        orderMapper.updateById(order);
    }

    private void upsertBindingFromPaid(AdminUser user, VipOrder order, Map<?, ?> userMap) {
        Long localUserId = localUserId(user);
        CommunityUserBinding binding = getBinding(localUserId);
        if (binding == null) {
            binding = new CommunityUserBinding();
            binding.setLocalUserId(localUserId);
            binding.setDeploymentId(order.getDeploymentId());
            binding.setNewApiBaseUrl(properties.getBaseUrl());
        }
        binding.setCommunityUserId(order.getCommunityUserId());
        binding.setCommunityUid(stringValue(userMap.get("community_uid")));
        binding.setInitialPayChannel(stringValue(userMap.get("initial_pay_channel")));
        binding.setInitialChannelPrefix(expectedPrefix(order.getPayChannel()));
        binding.setStatus("ACTIVE");
        binding.setLastSyncAt(LocalDateTime.now());
        if (binding.getId() == null) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
    }

    private void upsertSubscription(AdminUser user, VipOrder order, Map<?, ?> entitlement, String communityUid) {
        Long localUserId = localUserId(user);
        VipSubscription sub = getSubscription(localUserId);
        if (sub == null) {
            sub = new VipSubscription();
            sub.setLocalUserId(localUserId);
            sub.setDeploymentId(order.getDeploymentId());
        }
        sub.setCommunityUserId(order.getCommunityUserId());
        sub.setCommunityUid(communityUid);
        sub.setLicenseId(stringValue(entitlement.get("license_id")));
        sub.setVipLevel(defaultString(stringValue(entitlement.get("vip_level")), "pro"));
        sub.setFeaturesJson(toJson(entitlement.get("features")));
        sub.setLimitsJson(toJson(entitlement.get("limits")));
        sub.setStartedAt(fromEpoch(asLong(entitlement.get("started_at"))));
        sub.setExpiredAt(fromEpoch(asLong(entitlement.get("expired_at"))));
        sub.setStatus("ACTIVE");
        sub.setSourceOrderNo(order.getNewApiOrderNo());
        sub.setSignature(stringValue(entitlement.get("signature")));
        sub.setLastVerifiedAt(LocalDateTime.now());
        if (sub.getId() == null) {
            subscriptionMapper.insert(sub);
        } else {
            subscriptionMapper.updateById(sub);
        }
    }

    private Map<String, Object> ensureBinding(AdminUser user) {
        Long localUserId = localUserId(user);
        CommunityUserBinding existing = getBinding(localUserId);
        if (existing != null && existing.getCommunityUserId() != null && existing.getCommunityUserId() > 0
                && existing.getDeploymentId() != null && !existing.getDeploymentId().isBlank()
                && existing.getBindToken() != null && !existing.getBindToken().isBlank()) {
            return bindingMap(existing, false);
        }
        SdkDeployment deployment = ensureDeployment();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deployment_id", deployment.getDeploymentId());
        body.put("local_user_id", String.valueOf(localUserId));
        body.put("username", user != null ? user.getUsername() : "admin");
        body.put("display_name", user != null && user.getDisplayName() != null ? user.getDisplayName() : "管理员");
        body.put("domain", deployment.getServerUrl() == null ? "" : deployment.getServerUrl());
        body.put("version", "0.0.4");
        Map<String, Object> response;
        try {
            response = externalPost("/api/community/external/social-sdk/users/bind", body);
        } catch (Exception e) {
            throw new IllegalStateException("无法连接 I 社区完成账户绑定：" + e.getMessage(), e);
        }
        CommunityUserBinding binding = existing != null ? existing : new CommunityUserBinding();
        binding.setLocalUserId(localUserId);
        binding.setDeploymentId(deployment.getDeploymentId());
        binding.setCommunityUserId(asLong(response.getOrDefault("community_user_id", 0L)));
        binding.setCommunityUid(stringValue(response.get("community_uid")));
        binding.setBindId(stringValue(response.get("bind_id")));
        binding.setBindToken(stringValue(response.get("bind_token")));
        binding.setNewApiBaseUrl(properties.getBaseUrl());
        binding.setStatus(defaultString(stringValue(response.get("bind_status")), "PENDING").toUpperCase(Locale.ROOT));
        binding.setWechatBound(false);
        binding.setEmailBound(false);
        binding.setLastSyncAt(LocalDateTime.now());
        if (binding.getId() == null) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
        return bindingMap(binding, existing == null);
    }

    private SdkDeployment ensureDeployment() {
        SdkDeployment existing = deploymentMapper.selectOne(new LambdaQueryWrapper<SdkDeployment>().last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        SdkDeployment deployment = new SdkDeployment();
        deployment.setDeploymentId("sdk_dep_" + UUID.randomUUID().toString().replace("-", ""));
        deployment.setInstallTime(LocalDateTime.now());
        deploymentMapper.insert(deployment);
        return deployment;
    }

    private Map<String, Object> externalGet(String path) throws Exception {
        HttpRequest request = signedRequest("GET", path, "").GET().build();
        return send(request);
    }

    private Map<String, Object> externalPost(String path, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = signedRequest("POST", path, json).POST(HttpRequest.BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
        return send(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("I 社区返回异常: " + response.statusCode() + "，" + extractErrorMessage(response.body()));
        }
        Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Object success = payload.get("success");
        if (Boolean.FALSE.equals(success)) {
            throw new IllegalStateException(stringValue(payload.get("message")));
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return payload;
    }

    private HttpRequest.Builder signedRequest(String method, String path, String body) throws Exception {
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .header("X-App-Id", properties.getAppId())
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce);
        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256Hex(body == null ? "" : body);
            builder.header("X-Signature", hmacSha256Hex(properties.getSecret(), canonical));
        }
        return builder;
    }

    private HttpRequest.Builder signedCommunityRequest(String method, String path, String body, CommunityUserBinding binding) throws Exception {
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String bodyHash = sha256Hex(body == null ? "" : body);
        String signaturePayload = binding.getDeploymentId() + "\n" + nonce + "\n" + timestamp + "\n" + bodyHash;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .header("X-Tentacle-OpenId", binding.getDeploymentId())
                .header("X-Tentacle-ClientId", "social-sdk")
                .header("X-Tentacle-Timestamp", timestamp)
                .header("X-Tentacle-Nonce", nonce)
                .header("X-Tentacle-Body-SHA256", bodyHash)
                .header("X-Tentacle-Signature", hmacSha256Hex(binding.getBindToken(), signaturePayload));
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRaw(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("I 社区返回异常: " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private String normalizeProxyPath(String path) {
        if (path == null || path.isBlank()) {
            return "/topics";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.contains("..") || p.startsWith("/admin") || p.startsWith("/external")) {
            throw new IllegalArgumentException("非法社区路径");
        }
        return p;
    }

    private void validateChannelPrefix(String communityUid, String channel) {
        String prefix = expectedPrefix(channel);
        if (communityUid == null || !communityUid.startsWith(prefix + "-I-")) {
            throw new IllegalStateException("I 社区用户前缀与支付渠道不一致");
        }
    }

    private String expectedPrefix(String channel) {
        return switch (normalizeChannel(channel)) {
            case "alipay" -> "ALIX";
            case "wechat" -> "WXX";
            case "upay" -> "UX";
            case "manual" -> "MANX";
            default -> throw new IllegalArgumentException("未知支付渠道: " + channel);
        };
    }

    private String normalizeChannel(String channel) {
        String c = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "wechat_pay", "wxpay" -> "wechat";
            case "usdt", "virtual-pay", "virtual_pay" -> "upay";
            default -> c;
        };
    }

    private Map<String, Object> bindingMap(CommunityUserBinding binding, boolean isNew) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("community_user_id", binding.getCommunityUserId());
        map.put("community_uid", binding.getCommunityUid());
        map.put("bind_id", binding.getBindId());
        map.put("bind_status", binding.getStatus());
        map.put("bind_token", binding.getBindToken());
        map.put("is_new_user", isNew);
        return map;
    }

    private Map<String, Object> menu(String key, String label, String path) {
        return Map.of("key", key, "label", label, "path", path);
    }

    private CommunityUserBinding getBinding(Long localUserId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<CommunityUserBinding>().eq(CommunityUserBinding::getLocalUserId, localUserId).last("LIMIT 1"));
    }

    private VipSubscription getSubscription(Long localUserId) {
        return subscriptionMapper.selectOne(new LambdaQueryWrapper<VipSubscription>().eq(VipSubscription::getLocalUserId, localUserId).last("LIMIT 1"));
    }

    private VipOrder getLatestPendingOrder(Long localUserId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<VipOrder>()
                .eq(VipOrder::getLocalUserId, localUserId)
                .in(VipOrder::getStatus, "pending", "created", "pending_payment")
                .orderByDesc(VipOrder::getId)
                .last("LIMIT 1"));
    }

    private Long localUserId(AdminUser user) {
        return user != null && user.getId() != null ? user.getId() : DEFAULT_LOCAL_USER_ID;
    }

    private LocalDateTime fromEpoch(Long epoch) {
        if (epoch == null || epoch <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZONE);
    }

    private Object parseJson(String json, Object fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacSha256Hex(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return bytesToHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(body, new TypeReference<>() {});
            String message = stringValue(payload.get("message"));
            return message.isBlank() ? body : message;
        } catch (Exception ignored) {
            return body;
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String randomCode(int len) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, len).toUpperCase(Locale.ROOT);
    }
}
