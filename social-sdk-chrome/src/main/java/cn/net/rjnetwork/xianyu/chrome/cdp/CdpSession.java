package cn.net.rjnetwork.xianyu.chrome.cdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 持久化 CDP WebSocket 会话（单 target 连接复用）。
 *
 * <p>相对 {@link cn.net.rjnetwork.xianyu.chrome.core.ChromeSession#sendCdpCommand} 每次新建连接的
 * 一次性调用，本会话维护一条常驻 WebSocket：
 * <ul>
 *   <li>请求/响应按自增 id 匹配，支持并发命令（响应经 {@link CompletableFuture} 异步回填）</li>
 *   <li>支持事件订阅（{@link #on(String, CdpEventListener)}），可监听 Network/Page/Fetch 等域事件</li>
 *   <li>关闭时自动唤醒所有未完成命令并抛出异常，避免调用方永久挂起</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * try (CdpSession session = CdpSession.connect(wsUrl, httpClient)) {
 *     session.on("Network.responseReceived", (m, p) -> { ... });
 *     session.send("Network.enable", null);
 *     JsonNode result = session.send("Runtime.evaluate",
 *             JSON.createObjectNode().put("expression", "1+1").put("returnByValue", true));
 * }
 * }</pre>
 */
public class CdpSession implements Closeable {

    /** CDP 事件监听器（在 OkHttp 消息线程同步回调，回调内请勿执行耗时/阻塞操作）。 */
    public interface CdpEventListener {
        void onEvent(String method, JsonNode params);
    }

    private static final Logger log = LoggerFactory.getLogger(CdpSession.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String wsUrl;
    private final long defaultTimeoutSeconds;

    private volatile WebSocket ws;
    private volatile boolean open;

    private final AtomicLong idSeq = new AtomicLong(0);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CdpEventListener>> listeners = new ConcurrentHashMap<>();

    private CdpSession(OkHttpClient httpClient, String wsUrl, long defaultTimeoutSeconds) {
        this.httpClient = httpClient;
        this.wsUrl = wsUrl;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * 建立到指定 target 的持久 WebSocket 连接。
     *
     * @param wsUrl        target 的 webSocketDebuggerUrl（如 ws://127.0.0.1:9222/devtools/page/xxx）
     * @param httpClient   OkHttp 客户端（建议配置较长 readTimeout；内部会派生专用连接池）
     * @param connectTimeoutSeconds 建连超时（秒）
     * @param defaultCommandTimeoutSeconds 默认命令超时（秒）
     * @return 已连接的会话
     * @throws IOException 连接失败或超时
     */
    public static CdpSession connect(String wsUrl, OkHttpClient httpClient,
                                     long connectTimeoutSeconds, long defaultCommandTimeoutSeconds) throws IOException {
        if (wsUrl == null || wsUrl.isBlank()) {
            throw new IOException("CDP WebSocket URL 为空");
        }
        OkHttpClient client = (httpClient != null ? httpClient : new OkHttpClient())
                .newBuilder()
                .readTimeout(Math.max(30, defaultCommandTimeoutSeconds + 10), TimeUnit.SECONDS)
                .build();
        CdpSession session = new CdpSession(client, wsUrl, defaultCommandTimeoutSeconds);
        session.doConnect(connectTimeoutSeconds);
        return session;
    }

    /**
     * 建立连接，使用默认超时（建连 10s / 命令 15s）。
     */
    public static CdpSession connect(String wsUrl, OkHttpClient httpClient) throws IOException {
        return connect(wsUrl, httpClient, 10, 15);
    }

    private void doConnect(long connectTimeoutSeconds) throws IOException {
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<String> failReason = new AtomicReference<>();
        Request req = new Request.Builder().url(wsUrl).build();
        WebSocket socket = httpClient.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                CdpSession.this.ws = webSocket;
                open = true;
                opened.countDown();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                failReason.set(t != null ? t.getMessage() : "unknown");
                opened.countDown();
                failAllPending("连接失败: " + failReason.get());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                open = false;
                failAllPending("会话已关闭(" + code + ", " + reason + ")");
            }
        });
        this.ws = socket;
        try {
            if (!opened.await(connectTimeoutSeconds, TimeUnit.SECONDS)) {
                socket.close(1000, "connect-timeout");
                throw new IOException("CDP WebSocket 连接超时: " + wsUrl);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            socket.close(1000, "interrupted");
            throw new IOException("CDP WebSocket 连接被中断: " + wsUrl, ie);
        }
        if (!open) {
            socket.close(1000, "connect-failed");
            throw new IOException("CDP WebSocket 连接失败: " + wsUrl
                    + (failReason.get() != null ? ", reason=" + failReason.get() : ""));
        }
        log.debug("[CDP] 会话已连接: {}", wsUrl);
    }

    private void handleMessage(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            JsonNode idNode = node.get("id");
            if (idNode != null && idNode.isNumber()) {
                // 命令响应：唤醒对应等待方
                CompletableFuture<JsonNode> future = pending.remove(idNode.asLong());
                if (future != null) {
                    future.complete(node);
                }
            } else {
                // 事件推送：分发给订阅者
                String method = node.path("method").asText(null);
                if (method == null || method.isEmpty()) {
                    return;
                }
                JsonNode params = node.path("params");
                CopyOnWriteArrayList<CdpEventListener> list = listeners.get(method);
                if (list != null) {
                    for (CdpEventListener listener : list) {
                        try {
                            listener.onEvent(method, params);
                        } catch (Exception e) {
                            log.warn("[CDP] 事件处理异常 method={}, err={}", method, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CDP] 消息解析失败: {}", e.getMessage());
        }
    }

    /**
     * 异步发送 CDP 命令，返回的 future 在响应到达（或超时/失败）时完成。
     *
     * @param method CDP 方法名，如 {@code Runtime.evaluate}
     * @param params 参数（可为 null）
     */
    public CompletableFuture<JsonNode> sendAsync(String method, JsonNode params) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (!open) {
            future.completeExceptionally(new IOException("CDP WebSocket 未连接: " + wsUrl));
            return future;
        }
        long id = idSeq.incrementAndGet();
        pending.put(id, future);
        ObjectNode req = JSON.createObjectNode();
        req.put("id", id);
        req.put("method", method);
        if (params != null && !params.isEmpty()) {
            req.set("params", params);
        }
        WebSocket socket = this.ws;
        boolean sent = socket != null && socket.send(req.toString());
        if (!sent) {
            pending.remove(id);
            future.completeExceptionally(new IOException("CDP 消息发送失败: " + method));
        }
        return future;
    }

    /**
     * 同步发送 CDP 命令（默认超时），返回响应的 {@code result} 节点。
     *
     * @throws IOException     连接/发送失败，或 CDP 返回 error
     * @throws TimeoutException 命令超时
     */
    public JsonNode send(String method, JsonNode params) throws IOException, TimeoutException {
        return send(method, params, defaultTimeoutSeconds);
    }

    /**
     * 同步发送 CDP 命令（自定义超时），返回响应的 {@code result} 节点。
     */
    public JsonNode send(String method, JsonNode params, long timeoutSeconds) throws IOException, TimeoutException {
        try {
            JsonNode resp = sendAsync(method, params).get(timeoutSeconds, TimeUnit.SECONDS);
            JsonNode error = resp.get("error");
            if (error != null && !error.isNull()) {
                throw new IOException("CDP 命令失败: " + method + " -> " + error);
            }
            return resp.get("result");
        } catch (TimeoutException te) {
            throw new TimeoutException("CDP 命令超时: " + method + " (" + timeoutSeconds + "s)");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("CDP 调用被中断: " + method, ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("CDP 命令执行失败: " + method, cause);
        }
    }

    /**
     * 订阅指定 CDP 事件。
     */
    public void on(String method, CdpEventListener listener) {
        listeners.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 取消订阅指定 CDP 事件。
     */
    public void off(String method, CdpEventListener listener) {
        CopyOnWriteArrayList<CdpEventListener> list = listeners.get(method);
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * 会话是否处于连接状态。
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * 获取目标 WebSocket URL。
     */
    public String getWsUrl() {
        return wsUrl;
    }

    private void failAllPending(String reason) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> entry : pending.entrySet()) {
            CompletableFuture<JsonNode> future = pending.remove(entry.getKey());
            if (future != null) {
                future.completeExceptionally(new IOException("CDP 会话已终止: " + reason));
            }
        }
    }

    /**
     * 关闭会话，并唤醒所有未完成命令（抛异常）。
     */
    @Override
    public void close() {
        open = false;
        WebSocket socket = this.ws;
        if (socket != null) {
            try {
                socket.close(1000, "bye");
            } catch (Exception ignored) {
            }
        }
        failAllPending("closed");
        listeners.clear();
        log.debug("[CDP] 会话已关闭: {}", wsUrl);
    }
}
