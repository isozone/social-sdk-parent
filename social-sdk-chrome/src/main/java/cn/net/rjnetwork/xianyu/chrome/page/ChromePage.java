package cn.net.rjnetwork.xianyu.chrome.page;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 高级页面操作封装（单 page target）。
 *
 * <p>在持久 {@link CdpSession} 之上提供类 Selenium 的高层 API：
 * <ul>
 *   <li>导航与加载等待：{@link #navigate(String)} / {@link #waitForLoadState()}</li>
 *   <li>任意 JS 执行：{@link #evaluate(String)} / {@link #evalBool}/{@link #evalString}</li>
 *   <li>元素操作：{@link #$(String)} / {@link #click(String)} / {@link #type} / {@link #setValue} 等</li>
 *   <li>真实输入合成：{@link #mouseClick} / {@link #insertText} / {@link #pressKey}（CDP Input 域）</li>
 *   <li>截图 / 本地存储 / UA 覆盖 / Cookie（{@link #cookies()}）</li>
 * </ul>
 */
public class ChromePage implements Closeable {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 常见按键 → 虚拟键码（Input.dispatchKeyEvent 需要）。 */
    private static final Map<String, Integer> KEY_CODES = Map.ofEntries(
            Map.entry("Enter", 13), Map.entry("Tab", 9), Map.entry("Escape", 27),
            Map.entry("Backspace", 8), Map.entry("Delete", 46), Map.entry("Space", 32),
            Map.entry("ArrowUp", 38), Map.entry("ArrowDown", 40),
            Map.entry("ArrowLeft", 37), Map.entry("ArrowRight", 39),
            Map.entry("Home", 36), Map.entry("End", 35),
            Map.entry("PageUp", 33), Map.entry("PageDown", 34));

    private final CdpSession session;
    private final String targetId;

    public ChromePage(CdpSession session, String targetId) {
        this.session = session;
        this.targetId = targetId;
    }

    // ==================== 导航与加载 ====================

    /** 导航到 URL 并等待页面加载完成（默认 60s 超时）。 */
    public void navigate(String url) throws IOException, TimeoutException {
        navigate(url, 60);
    }

    /** 导航到 URL 并等待页面加载完成（SPA 场景 readyState 到 complete 可能需要更久）。 */
    public void navigate(String url, long timeoutSeconds) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("url", url);
        session.send("Page.navigate", params);
        waitForLoadState(timeoutSeconds);
    }

    /** 等待页面加载完成（readyState === 'complete'）。 */
    public void waitForLoadState() throws IOException, TimeoutException {
        waitForLoadState(60);
    }

    /** 等待页面加载完成，可自定义超时（秒）。 */
    public void waitForLoadState(long timeoutSeconds) throws IOException, TimeoutException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                String state = evalString("document.readyState");
                if ("complete".equals(state)) {
                    return;
                }
                if ("interactive".equals(state)) {
                    // SPA 白屏兜底：等待至少一次 requestAnimationFrame 后重新判断
                    Thread.sleep(200);
                    continue;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("等待页面加载被中断", ie);
            } catch (IOException | TimeoutException e) {
                // 页面可能正在导航中导致 evaluate 失败，重试
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("等待页面加载被中断", ie);
            }
        }
        throw new TimeoutException("等待页面加载超时: " + timeoutSeconds + "s, currentUrl=" + url());
    }

    /** 当前页面 URL。 */
    public String url() {
        try {
            return evalString("location.href");
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前页面标题。 */
    public String title() {
        try {
            return evalString("document.title");
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== JS 执行 ====================

    /**
     * 在页面执行 JS，返回 {@code returnByValue} 后的 value（原始 JSON 值）。
     *
     * @throws IOException     脚本异常（exceptionDetails）或 CDP 失败
     * @throws TimeoutException CDP 命令超时
     */
    public JsonNode evaluate(String expression) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("expression", expression);
        params.put("returnByValue", true);
        params.put("awaitPromise", true);
        JsonNode result = session.send("Runtime.evaluate", params);
        JsonNode exc = result.get("exceptionDetails");
        if (exc != null && !exc.isNull()) {
            throw new IOException("页面脚本执行异常: " + exc.path("text").asText() + exc.path("exception").asText());
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

    // ==================== 元素查询 ====================

    /** 创建元素句柄（不校验存在性，操作时实时查询）。 */
    public PageElement $(String selector) {
        return new PageElement(this, selector);
    }

    /** 元素是否存在。 */
    public boolean exists(String selector) throws IOException, TimeoutException {
        return $(selector).exists();
    }

    /** 元素是否可见。 */
    public boolean isVisible(String selector) throws IOException, TimeoutException {
        return $(selector).isVisible();
    }

    /** 等待元素出现并可见，返回元素句柄。 */
    public PageElement waitForSelector(String selector, long timeoutMs)
            throws IOException, TimeoutException, InterruptedException {
        return $(selector).waitForVisible(timeoutMs);
    }

    /** 匹配元素数量。 */
    public int count(String selector) throws IOException, TimeoutException {
        JsonNode v = evaluate("document.querySelectorAll(" + esc(selector) + ").length");
        return v != null ? v.asInt(0) : 0;
    }

    /** 批量取元素文本。 */
    public List<String> texts(String selector) throws IOException, TimeoutException {
        JsonNode v = evaluate("Array.from(document.querySelectorAll(" + esc(selector) + "))"
                + ".map(el => el.innerText || el.textContent || '')");
        List<String> list = new ArrayList<>();
        if (v != null && v.isArray()) {
            for (JsonNode n : v) {
                list.add(n.isNull() ? null : n.asText());
            }
        }
        return list;
    }

    /** 等待元素出现、可见且位置稳定（可点击），返回元素句柄。 */
    public PageElement waitForClickable(String selector, long timeoutMs)
            throws IOException, TimeoutException, InterruptedException {
        return $(selector).waitForClickable(timeoutMs);
    }

    // ==================== XPath 查询 ====================

    /** XPath 是否命中元素。 */
    public boolean xpathExists(String xpath) throws IOException, TimeoutException {
        return evalBool("document.evaluate(" + esc(xpath) + ", document, null, "
                + "XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue !== null");
    }

    /** XPath 命中元素数量。 */
    public int xpathCount(String xpath) throws IOException, TimeoutException {
        JsonNode v = evaluate("document.evaluate(" + esc(xpath) + ", document, null, "
                + "XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null).snapshotLength");
        return v != null ? v.asInt(0) : 0;
    }

    /** 批量取 XPath 命中元素文本。 */
    public List<String> xpathTexts(String xpath) throws IOException, TimeoutException {
        JsonNode v = evaluate("(() => { const r = document.evaluate(" + esc(xpath) + ", document, null, "
                + "XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null); "
                + "const out = []; for (let i = 0; i < r.snapshotLength; i++) { "
                + "out.push(r.snapshotItem(i).innerText || r.snapshotItem(i).textContent || ''); } return out; })()");
        List<String> list = new ArrayList<>();
        if (v != null && v.isArray()) {
            for (JsonNode n : v) {
                list.add(n.isNull() ? null : n.asText());
            }
        }
        return list;
    }

    // ==================== iframe / frame 穿透 ====================

    /** frame 描述（id + url + parentId）。 */
    public static final class FrameInfo {
        public final String frameId;
        public final String url;
        public final String parentId;

        FrameInfo(String frameId, String url, String parentId) {
            this.frameId = frameId;
            this.url = url;
            this.parentId = parentId;
        }

        @Override
        public String toString() {
            return "FrameInfo{id='" + frameId + "', url='" + url + "', parent=" + parentId + "}";
        }
    }

    /**
     * 列出当前页面所有 frame（含主 frame，按树序深度优先）。
     * 底层走 {@code Page.getFrameTree}，可拿到 iframe 的 id 与 url。
     */
    public List<FrameInfo> frames() throws IOException, TimeoutException {
        JsonNode tree = session.send("Page.getFrameTree", null);
        List<FrameInfo> out = new ArrayList<>();
        collectFrames(tree.path("frameTree"), out);
        return out;
    }

    /** 主 frame（页面本身的执行上下文，与直接 evaluate 等价）。 */
    public ChromeFrame mainFrame() throws IOException, TimeoutException {
        List<FrameInfo> list = frames();
        if (list.isEmpty()) {
            throw new IOException("未获取到任何 frame");
        }
        return ChromeFrame.create(session, list.get(0).frameId);
    }

    /** 按 URL 包含匹配 frame（返回第一个命中的可执行句柄；url 为空视为匹配主 frame）。 */
    public ChromeFrame frameByUrl(String urlContains) throws IOException, TimeoutException {
        List<FrameInfo> list = frames();
        if (urlContains == null || urlContains.isBlank()) {
            return mainFrame();
        }
        for (FrameInfo f : list) {
            if (f.url != null && f.url.contains(urlContains)) {
                return ChromeFrame.create(session, f.frameId);
            }
        }
        throw new IOException("未找到 URL 包含 '" + urlContains + "' 的 frame, frames=" + list);
    }

    /** 按索引取 frame（0 = 主 frame，1..n = 树序后续 frame）。 */
    public ChromeFrame frameAt(int index) throws IOException, TimeoutException {
        List<FrameInfo> list = frames();
        if (index < 0 || index >= list.size()) {
            throw new IOException("frame 索引越界: " + index + ", 共 " + list.size() + " 个");
        }
        return ChromeFrame.create(session, list.get(index).frameId);
    }

    private static void collectFrames(JsonNode node, List<FrameInfo> out) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        JsonNode frame = node.path("frame");
        if (!frame.isNull() && !frame.isMissingNode()) {
            out.add(new FrameInfo(
                    frame.path("id").asText(),
                    frame.path("url").asText(null),
                    frame.path("parentId").asText(null)));
        }
        JsonNode children = node.path("childFrames");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectFrames(child, out);
            }
        }
    }

    // ==================== 元素操作（快捷入口） ====================

    public void click(String selector) throws IOException, TimeoutException {
        $(selector).click();
    }

    public void type(String selector, String text) throws IOException, TimeoutException {
        $(selector).type(text);
    }

    public void setValue(String selector, String value) throws IOException, TimeoutException {
        $(selector).setValue(value);
    }

    public void press(String selector, String key) throws IOException, TimeoutException {
        $(selector).click();
        pressKey(key);
    }

    public String text(String selector) throws IOException, TimeoutException {
        return $(selector).getText();
    }

    public String attr(String selector, String name) throws IOException, TimeoutException {
        return $(selector).getAttribute(name);
    }

    public String value(String selector) throws IOException, TimeoutException {
        return $(selector).getValue();
    }

    /** 下拉选择：按 option value 选中。 */
    public boolean selectValue(String selector, String value) throws IOException, TimeoutException {
        return $(selector).selectValue(value);
    }

    /** 下拉选择：按 option 显示文本选中。 */
    public boolean selectByLabel(String selector, String label) throws IOException, TimeoutException {
        return $(selector).selectByLabel(label);
    }

    /** 下拉选择：按索引选中（0 起）。 */
    public boolean selectByIndex(String selector, int index) throws IOException, TimeoutException {
        return $(selector).selectByIndex(index);
    }

    /**
     * 文件上传：给 {@code <input type="file">} 设置本地文件路径。
     * 走 {@code DOM.setFileInputFiles}，绕过页面安全限制，无需真实文件选择框。
     *
     * @param selector 文件 input 的 CSS 选择器
     * @param filePaths 一个或多个本地文件绝对路径
     */
    public void uploadFiles(String selector, List<String> filePaths) throws IOException, TimeoutException {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("filePaths 不能为空");
        }
        // 1. 取文档根节点
        ObjectNode doc = JSON.createObjectNode();
        doc.put("depth", -1);
        JsonNode root = session.send("DOM.getDocument", doc);
        int rootNodeId = root.path("root").path("nodeId").asInt(-1);
        if (rootNodeId < 0) {
            throw new IOException("DOM.getDocument 未返回 nodeId");
        }
        // 2. 按选择器定位 input 节点
        ObjectNode query = JSON.createObjectNode();
        query.put("nodeId", rootNodeId);
        query.put("selector", selector);
        JsonNode hit = session.send("DOM.querySelector", query);
        int nodeId = hit.path("nodeId").asInt(-1);
        if (nodeId < 0) {
            throw new IOException("未找到文件上传元素: " + selector);
        }
        // 3. 注入文件
        ObjectNode setFiles = JSON.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = setFiles.putArray("files");
        for (String p : filePaths) {
            arr.add(p);
        }
        setFiles.put("nodeId", nodeId);
        session.send("DOM.setFileInputFiles", setFiles);
    }

    // ==================== 弹窗处理 ====================

    /**
     * 自动处理 alert / confirm / prompt 弹窗：出现即点击「确定」。
     * 订阅 {@code Page.javascriptDialogOpening} 事件并应答 {@code Page.handleJavaScriptDialog}，
     * 防止弹窗阻塞后续自动化操作。
     *
     * <p>需要调用 {@link #enableDialogsAutoAccept()} 启用（默认不启用，避免隐藏业务弹窗）。
     */
    public void enableDialogsAutoAccept() {
        session.on("Page.javascriptDialogOpening", (method, params) -> {
            try {
                ObjectNode p = JSON.createObjectNode();
                p.put("accept", true);
                session.send("Page.handleJavaScriptDialog", p);
            } catch (Exception e) {
                // 弹窗已被关闭或会话断开，忽略
            }
        });
        try {
            ObjectNode params = JSON.createObjectNode();
            params.put("enabled", true);
            session.send("Page.enable", params);
        } catch (IOException | TimeoutException ignored) {
        }
    }

    public void scrollIntoView(String selector) throws IOException, TimeoutException {
        $(selector).scrollIntoView();
    }

    /** 页面滚动到绝对坐标。 */
    public void scrollTo(int x, int y) throws IOException, TimeoutException {
        evalBool("window.scrollTo(" + x + ", " + y + ") || true");
    }

    // ==================== 真实输入合成（CDP Input 域） ====================

    /** 在视口坐标 (x, y) 处执行一次完整鼠标点击（mousePressed + mouseReleased）。 */
    public void mouseClick(int x, int y) throws IOException, TimeoutException {
        ObjectNode pressed = JSON.createObjectNode();
        pressed.put("type", "mousePressed");
        pressed.put("x", x);
        pressed.put("y", y);
        pressed.put("button", "left");
        pressed.put("clickCount", 1);
        session.send("Input.dispatchMouseEvent", pressed);

        ObjectNode released = pressed.deepCopy();
        released.put("type", "mouseReleased");
        session.send("Input.dispatchMouseEvent", released);
    }

    /** 鼠标移动到视口坐标 (x, y)。 */
    public void mouseMove(int x, int y) throws IOException, TimeoutException {
        ObjectNode moved = JSON.createObjectNode();
        moved.put("type", "mouseMoved");
        moved.put("x", x);
        moved.put("y", y);
        moved.put("button", "none");
        session.send("Input.dispatchMouseEvent", moved);
    }

    /** 向当前聚焦元素插入文本（中文等复杂文本用 insertText 最稳）。 */
    public void insertText(String text) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("text", text == null ? "" : text);
        session.send("Input.insertText", params);
    }

    /** 按下按键（Enter/Tab/Escape/Backspace/Delete/方向键等）。 */
    public void pressKey(String key) throws IOException, TimeoutException {
        Integer vk = KEY_CODES.get(key);
        if (vk == null) {
            throw new IllegalArgumentException("不支持的按键: " + key + "，可选: " + KEY_CODES.keySet());
        }
        ObjectNode down = JSON.createObjectNode();
        down.put("type", "keyDown");
        down.put("key", key);
        down.put("code", key);
        down.put("windowsVirtualKeyCode", vk);
        down.put("nativeVirtualKeyCode", vk);
        session.send("Input.dispatchKeyEvent", down);

        ObjectNode up = down.deepCopy();
        up.put("type", "keyUp");
        session.send("Input.dispatchKeyEvent", up);
    }

    // ==================== 截图 ====================

    /** 截取当前视口 PNG 图片。 */
    public byte[] screenshotPng() throws IOException, TimeoutException {
        return Base64.getDecoder().decode(screenshotBase64("png"));
    }

    /** 截取当前视口图片（format: png / jpeg），返回 base64。 */
    public String screenshotBase64(String format) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("format", "jpeg".equalsIgnoreCase(format) ? "jpeg" : "png");
        if ("jpeg".equalsIgnoreCase(format)) {
            params.put("quality", 80);
        }
        JsonNode result = session.send("Page.captureScreenshot", params);
        return result.path("data").asText();
    }

    /** 整页截图（滚动拼接不可靠，改用 captureBeyondViewport 一次性截全页）。 */
    public byte[] screenshotFullPagePng() throws IOException, TimeoutException {
        return Base64.getDecoder().decode(screenshotFullPageBase64("png"));
    }

    /** 整页截图（format: png / jpeg），返回 base64。 */
    public String screenshotFullPageBase64(String format) throws IOException, TimeoutException {
        // 1. 取整页内容尺寸（cssContentSize 比 contentSize 更准，含滚动条外的完整布局）
        JsonNode metrics = session.send("Page.getLayoutMetrics", null);
        double width = metrics.path("cssContentSize").path("width").asDouble();
        double height = metrics.path("cssContentSize").path("height").asDouble();
        if (width <= 0 || height <= 0) {
            JsonNode content = metrics.path("contentSize");
            width = content.path("width").asDouble();
            height = content.path("height").asDouble();
        }
        if (width <= 0 || height <= 0) {
            throw new IOException("无法获取页面内容尺寸，整页截图失败");
        }
        // 2. captureBeyondViewport + clip 覆盖整页
        ObjectNode params = JSON.createObjectNode();
        params.put("format", "jpeg".equalsIgnoreCase(format) ? "jpeg" : "png");
        if ("jpeg".equalsIgnoreCase(format)) {
            params.put("quality", 80);
        }
        params.put("captureBeyondViewport", true);
        ObjectNode clip = params.putObject("clip");
        clip.put("x", 0);
        clip.put("y", 0);
        clip.put("width", width);
        clip.put("height", height);
        clip.put("scale", 1);
        JsonNode result = session.send("Page.captureScreenshot", params);
        return result.path("data").asText();
    }

    // ==================== 本地 / 会话存储 ====================

    public String localStorage(String key) throws IOException, TimeoutException {
        return evalString("localStorage.getItem(" + esc(key) + ")");
    }

    public void setLocalStorage(String key, String value) throws IOException, TimeoutException {
        evalBool("(localStorage.setItem(" + esc(key) + ", " + esc(value) + "), true)");
    }

    public String sessionStorage(String key) throws IOException, TimeoutException {
        return evalString("sessionStorage.getItem(" + esc(key) + ")");
    }

    public void setSessionStorage(String key, String value) throws IOException, TimeoutException {
        evalBool("(sessionStorage.setItem(" + esc(key) + ", " + esc(value) + "), true)");
    }

    // ==================== UA / Cookie / 会话 ====================

    /** 覆盖 User-Agent（Network.setUserAgentOverride）。 */
    public void setUserAgent(String userAgent) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("userAgent", userAgent);
        session.send("Network.setUserAgentOverride", params);
    }

    /** Cookie 操作入口。 */
    public CdpCookieStore cookies() {
        return new CdpCookieStore(session);
    }

    /** 底层 CDP 会话（需要自定义命令时使用）。 */
    public CdpSession session() {
        return session;
    }

    /** target id。 */
    public String getTargetId() {
        return targetId;
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() {
        session.close();
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
