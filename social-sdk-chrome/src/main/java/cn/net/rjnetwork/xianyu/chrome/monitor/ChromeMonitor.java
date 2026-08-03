package cn.net.rjnetwork.xianyu.chrome.monitor;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 页面运行时监控器：Console 日志 / JS 异常 / 性能指标 / 崩溃现场快照。
 *
 * <p>能力：
 * <ul>
 *   <li>{@link #enable()} — 订阅 {@code Runtime.consoleAPICalled} 与 {@code Runtime.exceptionThrown}，
 *       环形缓冲保留最近 N 条（默认 200），供排障回溯</li>
 *   <li>{@link #performanceMetrics()} — {@code Performance.getMetrics} 采集页面性能指标</li>
 *   <li>{@link #crashSnapshot()} — 崩溃现场快照：自动截图 + 最近 console + 异常 + 页面 URL/标题，
 *       一条调用拿全崩溃上下文，供日志上报 / 人工排查</li>
 * </ul>
 *
 * <p>非持久化封装，随 {@link CdpSession} 关闭而失效。
 */
public class ChromeMonitor implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ChromeMonitor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 环形缓冲默认容量。 */
    private static final int DEFAULT_BUFFER_SIZE = 200;

    /** 一条 console 日志。 */
    public static final class ConsoleEntry {
        public final long timestamp;
        /** log / warning / error / debug / info 等。 */
        public final String type;
        /** 拼接后的文本内容。 */
        public final String text;

        private ConsoleEntry(long timestamp, String type, String text) {
            this.timestamp = timestamp;
            this.type = type;
            this.text = text;
        }

        @Override
        public String toString() {
            return "ConsoleEntry{" + type + ", " + text + "}";
        }
    }

    /** 一条 JS 异常。 */
    public static final class ExceptionEntry {
        public final long timestamp;
        /** 异常摘要（exceptionDetails.text）。 */
        public final String text;
        /** 异常描述（exception.description，含堆栈）。 */
        public final String description;

        private ExceptionEntry(long timestamp, String text, String description) {
            this.timestamp = timestamp;
            this.text = text;
            this.description = description;
        }

        @Override
        public String toString() {
            return "ExceptionEntry{" + text + "}";
        }
    }

    /** 崩溃现场快照。 */
    public static final class CrashSnapshot {
        public final long timestamp;
        public final String url;
        public final String title;
        /** 截图 base64（PNG），截图失败时为 null。 */
        public final String screenshotBase64;
        public final List<ConsoleEntry> recentConsole;
        public final List<ExceptionEntry> recentExceptions;

        private CrashSnapshot(long timestamp, String url, String title, String screenshotBase64,
                              List<ConsoleEntry> recentConsole, List<ExceptionEntry> recentExceptions) {
            this.timestamp = timestamp;
            this.url = url;
            this.title = title;
            this.screenshotBase64 = screenshotBase64;
            this.recentConsole = recentConsole;
            this.recentExceptions = recentExceptions;
        }

        @Override
        public String toString() {
            return String.format("CrashSnapshot{ts=%d, url='%s', title='%s', console=%d, exceptions=%d, screenshot=%s}",
                    timestamp, url, title, recentConsole.size(), recentExceptions.size(),
                    screenshotBase64 != null ? screenshotBase64.length() + "chars" : "null");
        }
    }

    private final CdpSession session;
    private final int bufferSize;

    private final List<ConsoleEntry> consoleBuffer = new ArrayList<>();
    private final List<ExceptionEntry> exceptionBuffer = new ArrayList<>();

    private volatile boolean enabled;

    public ChromeMonitor(CdpSession session) {
        this(session, DEFAULT_BUFFER_SIZE);
    }

    public ChromeMonitor(CdpSession session, int bufferSize) {
        this.session = session;
        this.bufferSize = Math.max(10, bufferSize);
    }

    /** 订阅 console 与异常事件（幂等）。 */
    public synchronized void enable() throws IOException, TimeoutException {
        if (enabled) {
            return;
        }
        session.on("Runtime.consoleAPICalled", (m, p) -> onConsoleApiCalled(p));
        session.on("Runtime.exceptionThrown", (m, p) -> onExceptionThrown(p));
        session.send("Runtime.enable", null);
        enabled = true;
        log.debug("[MONITOR] 运行时监控已启用");
    }

    /** 最近 console 日志（按时间升序，不可变）。 */
    public synchronized List<ConsoleEntry> consoleLog() {
        return List.copyOf(consoleBuffer);
    }

    /** 最近 JS 异常（按时间升序，不可变）。 */
    public synchronized List<ExceptionEntry> exceptions() {
        return List.copyOf(exceptionBuffer);
    }

    /** 最近 N 条是否包含 error / warning 级别日志。 */
    public synchronized boolean hasErrors() {
        for (ConsoleEntry e : consoleBuffer) {
            if ("error".equals(e.type) || "assert".equals(e.type)) {
                return true;
            }
        }
        return !exceptionBuffer.isEmpty();
    }

    /** 清空缓冲。 */
    public synchronized void clear() {
        consoleBuffer.clear();
        exceptionBuffer.clear();
    }

    /**
     * 采集页面性能指标（{@code Performance.enable} + {@code Performance.getMetrics}）。
     * 返回指标名 → 值，如 Timestamp / Documents / JSHeapUsedSize / Nodes 等。
     */
    public Map<String, Double> performanceMetrics() throws IOException, TimeoutException {
        session.send("Performance.enable", null);
        JsonNode result = session.send("Performance.getMetrics", null);
        Map<String, Double> map = new LinkedHashMap<>();
        JsonNode metrics = result.path("metrics");
        if (metrics.isArray()) {
            for (JsonNode m : metrics) {
                map.put(m.path("name").asText(), m.path("value").asDouble());
            }
        }
        return map;
    }

    /**
     * 生成崩溃现场快照：截图 + 最近 console + 异常 + 页面 URL/标题。
     * 任一子项失败不影响其他子项（截图失败时 screenshotBase64 为 null）。
     */
    public CrashSnapshot crashSnapshot() {
        long ts = System.currentTimeMillis();
        String url = safeEvalString("location.href");
        String title = safeEvalString("document.title");
        String shot = safeScreenshot();
        List<ConsoleEntry> console;
        List<ExceptionEntry> exceptions;
        synchronized (this) {
            console = List.copyOf(consoleBuffer);
            exceptions = List.copyOf(exceptionBuffer);
        }
        return new CrashSnapshot(ts, url, title, shot, console, exceptions);
    }

    // ==================== 内部 ====================

    private void onConsoleApiCalled(JsonNode p) {
        String type = p.path("type").asText("log");
        JsonNode args = p.path("args");
        StringBuilder sb = new StringBuilder();
        if (args.isArray()) {
            for (JsonNode arg : args) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(formatArg(arg));
            }
        }
        long ts = (long) (p.path("timestamp").asDouble(0) * 1000);
        appendConsole(new ConsoleEntry(ts == 0 ? System.currentTimeMillis() : ts, type, sb.toString()));
    }

    private void onExceptionThrown(JsonNode p) {
        JsonNode details = p.path("exceptionDetails");
        String text = details.path("text").asText("");
        String description = details.path("exception").path("description").asText(null);
        if (description == null || description.isEmpty()) {
            description = details.path("exception").path("value").asText(null);
        }
        long ts = (long) (p.path("timestamp").asDouble(0) * 1000);
        appendException(new ExceptionEntry(ts == 0 ? System.currentTimeMillis() : ts, text, description));
    }

    /** 把 console 参数格式化为一串（优先取 value，其次 description）。 */
    private static String formatArg(JsonNode arg) {
        if (arg == null || arg.isNull()) {
            return "null";
        }
        JsonNode value = arg.path("value");
        if (!value.isMissingNode() && !value.isNull()) {
            return value.isTextual() ? value.asText() : value.toString();
        }
        String desc = arg.path("description").asText(null);
        return desc != null ? desc : arg.toString();
    }

    private synchronized void appendConsole(ConsoleEntry entry) {
        consoleBuffer.add(entry);
        trim(consoleBuffer);
    }

    private synchronized void appendException(ExceptionEntry entry) {
        exceptionBuffer.add(entry);
        trim(exceptionBuffer);
    }

    private void trim(List<?> buffer) {
        while (buffer.size() > bufferSize) {
            buffer.remove(0);
        }
    }

    private String safeEvalString(String expression) {
        try {
            ObjectNode params = JSON.createObjectNode();
            params.put("expression", expression);
            params.put("returnByValue", true);
            JsonNode result = session.send("Runtime.evaluate", params);
            JsonNode v = result.path("result").path("value");
            return v.isNull() || v.isMissingNode() ? null : v.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeScreenshot() {
        try {
            ObjectNode params = JSON.createObjectNode();
            params.put("format", "png");
            JsonNode result = session.send("Page.captureScreenshot", params);
            return result.path("data").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        // 会话关闭时事件订阅自然失效；无需额外命令
    }
}
