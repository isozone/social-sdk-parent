package cn.net.rjnetwork.xianyu.chrome.network;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 网络域监听与请求拦截（基于 CDP Network / Fetch 域）。
 *
 * <p>能力：
 * <ul>
 *   <li>{@link #enable()} — 启用 Network 域，自动记录请求/响应日志（{@link RequestRecord}）</li>
 *   <li>{@link #onRequest}/{@link #onResponse} — 订阅请求发出 / 响应到达事件</li>
 *   <li>{@link #interceptAll()} / {@link #intercept(List)} — 启用 Fetch 请求拦截；未设置处理器时自动放行</li>
 *   <li>{@link #onRequestPaused} — 拦截回调，可调用 {@link #continueRequest}/{@link #failRequest}/{@link #fulfillRequest} 放行/失败/伪造响应</li>
 *   <li>{@link #snapshot()} / {@link #find(String)} — 读取请求日志</li>
 * </ul>
 *
 * <p>非持久化封装，随 {@link CdpSession} 关闭而失效。
 */
public class ChromeNetwork implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ChromeNetwork.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 一条请求的完整生命周期记录（requestWillBeSent → responseReceived 合并）。 */
    public static final class RequestRecord {
        public final String requestId;
        public final String url;
        public final String method;
        public final String resourceType;
        public final String postData;
        public final Map<String, String> requestHeaders;
        public final Integer status;
        public final String mimeType;
        public final String remoteIpAddress;
        public final String failureText;
        public final long startedAt;

        private RequestRecord(String requestId, String url, String method, String resourceType,
                              String postData, Map<String, String> requestHeaders,
                              Integer status, String mimeType, String remoteIpAddress,
                              String failureText, long startedAt) {
            this.requestId = requestId;
            this.url = url;
            this.method = method;
            this.resourceType = resourceType;
            this.postData = postData;
            this.requestHeaders = requestHeaders;
            this.status = status;
            this.mimeType = mimeType;
            this.remoteIpAddress = remoteIpAddress;
            this.failureText = failureText;
            this.startedAt = startedAt;
        }

        @Override
        public String toString() {
            return String.format("RequestRecord{method=%s, status=%s, url='%s'}", method, status, url);
        }
    }

    private final CdpSession session;

    /** 保序请求日志（requestId → 记录，snapshot 时按 startedAt 排序）。 */
    private final ConcurrentHashMap<String, RequestRecord> requests = new ConcurrentHashMap<>();
    /** 记录顺序（startedAt 单调递增）。 */
    private final List<String> order = new CopyOnWriteArrayList<>();
    /** 响应体缓存（requestId → body；仅在开启自动捕获或调用 body() 时填充）。 */
    private final ConcurrentHashMap<String, String> bodies = new ConcurrentHashMap<>();

    private final CopyOnWriteArrayList<Consumer<RequestRecord>> requestListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<RequestRecord>> responseListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<JsonNode>> pausedListeners = new CopyOnWriteArrayList<>();

    private volatile boolean networkEnabled;
    private volatile boolean interceptionEnabled;
    /** 是否在响应到达后自动抓取响应体（异步，不阻塞事件回调）。 */
    private volatile boolean captureBodies;

    public ChromeNetwork(CdpSession session) {
        this.session = session;
    }

    // ==================== 启用 / 事件订阅 ====================

    /** 启用 Network 域并挂载请求/响应记录。重复调用幂等。 */
    public synchronized void enable() throws IOException, TimeoutException {
        enable(false);
    }

    /**
     * 启用 Network 域并挂载请求/响应记录。
     *
     * @param captureResponseBodies true = 每个响应到达后异步抓取响应体并缓存
     *                              （走 {@code Network.getResponseBody}，供 {@link #body(String)} 直接读取）
     */
    public synchronized void enable(boolean captureResponseBodies) throws IOException, TimeoutException {
        this.captureBodies = captureResponseBodies;
        if (networkEnabled) {
            return;
        }
        session.on("Network.requestWillBeSent", (m, p) -> onRequestWillBeSent(p));
        session.on("Network.responseReceived", (m, p) -> onResponseReceived(p));
        session.on("Network.loadingFailed", (m, p) -> onLoadingFailed(p));
        session.send("Network.enable", null);
        networkEnabled = true;
        log.debug("[NET] Network 域已启用, captureBodies={}", captureResponseBodies);
    }

    /**
     * 设置全局附加请求头（{@code Network.setExtraHTTPHeaders}），对该上下文后续所有请求生效。
     * 常用场景：给 API 调用附加自定义签名头、trace 头等。
     */
    public void setExtraHeaders(Map<String, String> headers) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        ObjectNode h = JSON.createObjectNode();
        if (headers != null) {
            headers.forEach(h::put);
        }
        params.set("headers", h);
        session.send("Network.setExtraHTTPHeaders", params);
        log.debug("[NET] 已设置附加请求头: {}", headers);
    }

    /** 订阅请求发出事件（requestWillBeSent）。 */
    public void onRequest(Consumer<RequestRecord> listener) {
        requestListeners.add(listener);
    }

    /** 订阅响应到达事件（responseReceived）。 */
    public void onResponse(Consumer<RequestRecord> listener) {
        responseListeners.add(listener);
    }

    // ==================== 请求日志 ====================

    /** 请求日志快照（按发起时间排序，不可变）。 */
    public List<RequestRecord> snapshot() {
        List<RequestRecord> list = new ArrayList<>(requests.size());
        for (String id : order) {
            RequestRecord r = requests.get(id);
            if (r != null) {
                list.add(r);
            }
        }
        return List.copyOf(list);
    }

    /** 按 URL 包含匹配查找请求记录（返回第一条）。 */
    public RequestRecord find(String urlContains) {
        for (String id : order) {
            RequestRecord r = requests.get(id);
            if (r != null && r.url != null && r.url.contains(urlContains)) {
                return r;
            }
        }
        return null;
    }

    /**
     * 读取指定请求的响应体（{@code Network.getResponseBody}）。
     * 开启自动捕获时直接命中缓存；否则惰性抓取（仅对已完成且仍可读的响应有效）。
     *
     * @return 响应体文本；请求无响应体 / 已不可读时返回 null
     */
    public String body(String requestId) throws IOException, TimeoutException {
        String cached = bodies.get(requestId);
        if (cached != null) {
            return cached;
        }
        ObjectNode params = JSON.createObjectNode();
        params.put("requestId", requestId);
        JsonNode result = session.send("Network.getResponseBody", params);
        String body = result.path("body").asText(null);
        boolean base64 = result.path("base64Encoded").asBoolean(false);
        if (body != null && base64) {
            body = decodeBase64(body);
        }
        if (body != null) {
            bodies.put(requestId, body);
        }
        return body;
    }

    /** 按 URL 包含匹配查找第一个可读响应体（API 抓包常用入口）。 */
    public String findBody(String urlContains) throws IOException, TimeoutException {
        for (String id : order) {
            RequestRecord r = requests.get(id);
            if (r != null && r.url != null && r.url.contains(urlContains)) {
                String body = body(id);
                if (body != null) {
                    return body;
                }
            }
        }
        return null;
    }

    /** 清空请求日志与响应体缓存（不影响事件订阅）。 */
    public void clear() {
        requests.clear();
        order.clear();
        bodies.clear();
    }

    private static String decodeBase64(String s) {
        try {
            return new String(java.util.Base64.getDecoder().decode(s), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    // ==================== Fetch 拦截 ====================

    /**
     * 启用全量请求拦截。未注册 {@link #onRequestPaused} 处理器时自动放行所有请求；
     * 注册处理器后，每个请求都会先进入处理器，必须调用
     * {@link #continueRequest}/{@link #failRequest}/{@link #fulfillRequest} 之一放行，否则请求挂起。
     */
    public synchronized void interceptAll() throws IOException, TimeoutException {
        intercept(List.of(Map.of("urlPattern", "*")));
    }

    /**
     * 启用指定模式拦截。
     *
     * @param patterns Fetch.enable 的 patterns，如 {@code List.of(Map.of("urlPattern", "*https://*.goofish.com/*", "requestStage", "Request"))}
     */
    public synchronized void intercept(List<Map<String, Object>> patterns) throws IOException, TimeoutException {
        if (interceptionEnabled) {
            return;
        }
        session.on("Fetch.requestPaused", (m, p) -> onRequestPaused(p));
        ObjectNode params = JSON.createObjectNode();
        ArrayNode arr = JSON.createArrayNode();
        for (Map<String, Object> pattern : patterns) {
            arr.add(JSON.valueToTree(pattern));
        }
        params.set("patterns", arr);
        session.send("Fetch.enable", params);
        interceptionEnabled = true;
        log.debug("[NET] Fetch 拦截已启用, patterns={}", patterns.size());
    }

    /** 注册拦截回调（可多个；回调内调用 continue/fail/fulfill 决定请求去向）。 */
    public void onRequestPaused(Consumer<JsonNode> handler) {
        pausedListeners.add(handler);
    }

    /** 放行被拦截的请求。 */
    public void continueRequest(String requestId) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("requestId", requestId);
        session.send("Fetch.continueRequest", params);
    }

    /** 中止被拦截的请求（errorReason: Failed/Aborted/AccessDenied/BlockedByClient 等）。 */
    public void failRequest(String requestId, String errorReason) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("requestId", requestId);
        params.put("errorReason", errorReason == null ? "Failed" : errorReason);
        session.send("Fetch.failRequest", params);
    }

    /** 直接伪造响应给被拦截的请求。 */
    public void fulfillRequest(String requestId, int statusCode, String contentType, String body) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("requestId", requestId);
        params.put("responseCode", statusCode);
        ObjectNode headers = JSON.createObjectNode();
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        params.set("responseHeaders", headers);
        if (body != null) {
            params.put("body", java.util.Base64.getEncoder().encodeToString(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        session.send("Fetch.fulfillRequest", params);
    }

    /** 关闭拦截（Fetch.disable）。 */
    public synchronized void disableInterception() throws IOException, TimeoutException {
        if (!interceptionEnabled) {
            return;
        }
        session.send("Fetch.disable", null);
        interceptionEnabled = false;
        pausedListeners.clear();
    }

    // ==================== 内部事件处理 ====================

    private void onRequestWillBeSent(JsonNode p) {
        String requestId = p.path("requestId").asText();
        JsonNode req = p.path("request");
        String url = req.path("url").asText();
        String method = req.path("method").asText();
        String postData = req.path("postData").asText(null);
        JsonNode headers = req.path("headers");
        Map<String, String> headerMap = new ConcurrentHashMap<>();
        if (headers != null && headers.isObject()) {
            headers.fields().forEachRemaining(e -> headerMap.put(e.getKey(), e.getValue().asText()));
        }
        RequestRecord record = new RequestRecord(
                requestId, url, method,
                p.path("type").asText(null),
                postData, Map.copyOf(headerMap),
                null, null, null, null, System.currentTimeMillis());
        requests.put(requestId, record);
        order.add(requestId);
        for (Consumer<RequestRecord> l : requestListeners) {
            try {
                l.accept(record);
            } catch (Exception e) {
                log.warn("[NET] onRequest 回调异常: {}", e.getMessage());
            }
        }
    }

    private void onResponseReceived(JsonNode p) {
        String requestId = p.path("requestId").asText();
        RequestRecord existing = requests.get(requestId);
        JsonNode resp = p.path("response");
        int status = resp.path("status").asInt(0);
        String mimeType = resp.path("mimeType").asText(null);
        String remoteIp = resp.path("remoteIPAddress").asText(null);
        if (existing == null) {
            // responseReceived 先于 requestWillBeSent（极少见），补一条
            RequestRecord record = new RequestRecord(requestId, resp.path("url").asText(),
                    null, null, null, Map.of(), status, mimeType, remoteIp, null, System.currentTimeMillis());
            requests.put(requestId, record);
            order.add(requestId);
            existing = record;
        } else {
            requests.put(requestId, new RequestRecord(
                    existing.requestId, existing.url, existing.method, existing.resourceType,
                    existing.postData, existing.requestHeaders,
                    status, mimeType, remoteIp, existing.failureText, existing.startedAt));
            existing = requests.get(requestId);
        }
        for (Consumer<RequestRecord> l : responseListeners) {
            try {
                l.accept(existing);
            } catch (Exception e) {
                log.warn("[NET] onResponse 回调异常: {}", e.getMessage());
            }
        }
        // 自动捕获响应体：异步抓取，不阻塞 CDP 事件回调线程
        if (captureBodies && status > 0) {
            ObjectNode params = JSON.createObjectNode();
            params.put("requestId", requestId);
            session.sendAsync("Network.getResponseBody", params).whenComplete((result, err) -> {
                if (err != null) {
                    return; // 部分响应（如流式/已回收）不可读，忽略
                }
                JsonNode res = result.path("result");
                String body = res.path("body").asText(null);
                if (body == null) {
                    return;
                }
                if (res.path("base64Encoded").asBoolean(false)) {
                    body = decodeBase64(body);
                }
                bodies.put(requestId, body);
            });
        }
    }

    private void onLoadingFailed(JsonNode p) {
        String requestId = p.path("requestId").asText();
        String error = p.path("errorText").asText("Failed");
        RequestRecord existing = requests.get(requestId);
        if (existing == null) {
            return;
        }
        requests.put(requestId, new RequestRecord(
                existing.requestId, existing.url, existing.method, existing.resourceType,
                existing.postData, existing.requestHeaders,
                existing.status, existing.mimeType, existing.remoteIpAddress, error, existing.startedAt));
    }

    private void onRequestPaused(JsonNode p) {
        String requestId = p.path("requestId").asText();
        if (pausedListeners.isEmpty()) {
            // 无处理器：自动放行，避免请求挂起
            try {
                continueRequest(requestId);
            } catch (Exception e) {
                log.warn("[NET] 自动放行拦截请求失败: {}", e.getMessage());
            }
            return;
        }
        for (Consumer<JsonNode> l : pausedListeners) {
            try {
                l.accept(p);
            } catch (Exception e) {
                log.warn("[NET] onRequestPaused 回调异常，放行请求: {}", e.getMessage());
                try {
                    continueRequest(requestId);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void close() {
        // 会话关闭时事件订阅自然失效；无需额外命令
    }
}
