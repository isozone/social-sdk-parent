package cn.net.rjnetwork.xianyu.chrome.page;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 页面内 frame（iframe / frame / object）的可执行句柄。
 *
 * <p>通过 CDP {@code Page.createIsolatedWorld} 为指定 frame 创建独立执行上下文，
 * 所有查询/求值都在该 frame 的 DOM 内进行，解决主页面 CSS 选择器无法穿透 iframe 的问题
 * （不受跨域限制，因为命令直接发往该 frame 的执行上下文）。
 *
 * <p>典型用法：
 * <pre>{@code
 * try (ChromePage page = chromeBrowser.openPage(accountId)) {
 *     page.navigate("https://example.com/");
 *     ChromeFrame frame = page.frameByUrl("embed");
 *     frame.waitForSelector(".login-btn", 10_000);
 *     frame.click(".login-btn");
 * }
 * }</pre>
 *
 * <p>注意：frame 内的点击使用 DOM 事件派发（{@code el.click()}），因为 iframe 的
 * 坐标系相对其自身视口，跨 frame 换算到顶层视口坐标容易出错；对验证码 / 表单类
 * 交互已足够。需要绝对坐标真实点击时，请使用 {@link ChromePage#mouseClick(int, int)}。
 */
public class ChromeFrame {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CdpSession session;
    private final String frameId;
    private final int executionContextId;

    ChromeFrame(CdpSession session, String frameId, int executionContextId) {
        this.session = session;
        this.frameId = frameId;
        this.executionContextId = executionContextId;
    }

    /** 为指定 frame 创建独立执行世界（每次调用新建上下文，world 间状态互相隔离）。 */
    public static ChromeFrame create(CdpSession session, String frameId) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("frameId", frameId);
        params.put("worldName", "sdk_frame_" + Integer.toHexString(frameId.hashCode()));
        params.put("grantUniveralAccess", true);
        JsonNode result = session.send("Page.createIsolatedWorld", params);
        int contextId = result.path("executionContextId").asInt(-1);
        if (contextId < 0) {
            throw new IOException("无法为 frame 创建执行上下文: " + frameId);
        }
        return new ChromeFrame(session, frameId, contextId);
    }

    /** frame id。 */
    public String getFrameId() {
        return frameId;
    }

    /** 在 frame 内执行 JS，返回 {@code returnByValue} 后的 value。 */
    public JsonNode evaluate(String expression) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("expression", expression);
        params.put("returnByValue", true);
        params.put("awaitPromise", true);
        params.put("executionContextId", executionContextId);
        JsonNode result = session.send("Runtime.evaluate", params);
        JsonNode exc = result.get("exceptionDetails");
        if (exc != null && !exc.isNull()) {
            throw new IOException("frame 脚本执行异常: " + exc.path("text").asText() + exc.path("exception").asText());
        }
        return result.path("result").get("value");
    }

    /** 执行 JS 并返回 boolean（null/false → false）。 */
    public boolean evalBool(String expression) throws IOException, TimeoutException {
        JsonNode v = evaluate(expression);
        return v != null && v.isBoolean() && v.asBoolean();
    }

    /** 执行 JS 并返回字符串（null → null）。 */
    public String evalString(String expression) throws IOException, TimeoutException {
        JsonNode v = evaluate(expression);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** 元素是否存在于 frame DOM。 */
    public boolean exists(String selector) throws IOException, TimeoutException {
        return evalBool("!!document.querySelector(" + esc(selector) + ")");
    }

    /** 等待元素出现（frame 内）。 */
    public void waitForSelector(String selector, long timeoutMs)
            throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (exists(selector)) {
                return;
            }
            Thread.sleep(150);
        }
        throw new TimeoutException("frame 内等待元素超时: " + selector + " (" + timeoutMs + "ms)");
    }

    /** 在 frame 内点击元素（滚动到可见 → focus → 派发 click 事件）。 */
    public void click(String selector) throws IOException, TimeoutException {
        boolean ok = evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return false; "
                + "el.scrollIntoView({block:'center', inline:'center'}); "
                + "el.focus(); el.click(); return true; })()");
        if (!ok) {
            throw new IOException("frame 内元素不存在: " + selector);
        }
    }

    /** 取元素文本（innerText，无元素返回 null）。 */
    public String text(String selector) throws IOException, TimeoutException {
        JsonNode v = evaluate("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "return el ? (el.innerText || el.textContent || '') : null; })()");
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** 在 frame 内输入文本（点击聚焦后 {@code Input.insertText}，支持中文）。 */
    public void type(String selector, String text) throws IOException, TimeoutException {
        click(selector);
        ObjectNode params = JSON.createObjectNode();
        params.put("text", text == null ? "" : text);
        session.send("Input.insertText", params);
    }

    /** 字符串 → JSON 字符串字面量（带引号，防注入）。 */
    private static String esc(String s) {
        try {
            return JSON.writeValueAsString(s == null ? "" : s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "\"\"";
        }
    }
}
