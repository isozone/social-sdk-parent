package cn.net.rjnetwork.xianyu.chrome.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.chrome.human.HumanDelay;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 页面元素封装（CSS 选择器驱动）。
 *
 * <p>每次操作都在当前 DOM 中重新查询元素（适配 SPA 动态渲染），并优先走 CDP
 * {@code Input.dispatchMouseEvent / Input.insertText / Input.dispatchKeyEvent} 合成真实输入，
 * 比纯 JS {@code el.click()} 更接近真人操作、更不容易被前端事件校验拦截。
 */
public class PageElement {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChromePage page;
    private final String selector;

    PageElement(ChromePage page, String selector) {
        this.page = page;
        this.selector = selector;
    }

    /** CSS 选择器原文。 */
    public String getSelector() {
        return selector;
    }

    /** 元素是否存在于当前 DOM。 */
    public boolean exists() throws IOException, TimeoutException {
        return page.evalBool("!!document.querySelector(" + esc(selector) + ")");
    }

    /** 元素是否存在且可见（有尺寸、display/visibility 未隐藏）。 */
    public boolean isVisible() throws IOException, TimeoutException {
        return page.evalBool(visibleJs(selector));
    }

    /** 等待元素出现并可见。 */
    public PageElement waitForVisible(long timeoutMs) throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isVisible()) {
                return this;
            }
            Thread.sleep(150);
        }
        throw new TimeoutException("等待元素可见超时: " + selector + " (" + timeoutMs + "ms)");
    }

    /** 等待元素消失（或不可见）。 */
    public void waitForDisappear(long timeoutMs) throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!exists() || !isVisible()) {
                return;
            }
            Thread.sleep(150);
        }
        throw new TimeoutException("等待元素消失超时: " + selector + " (" + timeoutMs + "ms)");
    }

    /**
     * 等待元素可点击：存在、可见、未禁用，且中心坐标连续两次采样一致（位置稳定，
     * 适配 SPA 懒加载 / 动画位移中的按钮）。
     */
    public PageElement waitForClickable(long timeoutMs) throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode prev = null;
        int stableTicks = 0;
        while (System.currentTimeMillis() < deadline) {
            if (isVisible() && !isDisabled()) {
                JsonNode c = center();
                if (c != null) {
                    boolean same = prev != null
                            && prev.path("x").asInt() == c.path("x").asInt()
                            && prev.path("y").asInt() == c.path("y").asInt()
                            && prev.path("w").asInt() == c.path("w").asInt()
                            && prev.path("h").asInt() == c.path("h").asInt();
                    if (same) {
                        stableTicks++;
                        if (stableTicks >= 2) {
                            return this;
                        }
                    } else {
                        stableTicks = 0;
                        prev = c;
                    }
                }
            }
            Thread.sleep(150);
        }
        throw new TimeoutException("等待元素可点击超时: " + selector + " (" + timeoutMs + "ms)");
    }

    /** 元素是否处于禁用态（disabled 或 aria-disabled）。 */
    public boolean isDisabled() throws IOException, TimeoutException {
        return page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "return el ? (el.disabled === true || el.getAttribute('aria-disabled') === 'true') : false; })()");
    }

    // ==================== 下拉选择（select） ====================

    /** 按 option 的 value 选中并派发 change 事件。 */
    public boolean selectValue(String value) throws IOException, TimeoutException {
        return page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el || !el.options) return false; "
                + "const target = Array.from(el.options).find(o => o.value === " + esc(value) + "); "
                + "if (!target) return false; "
                + "el.value = target.value; "
                + "el.dispatchEvent(new Event('change', {bubbles: true})); return true; })()");
    }

    /** 按 option 的显示文本选中并派发 change 事件。 */
    public boolean selectByLabel(String label) throws IOException, TimeoutException {
        return page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el || !el.options) return false; "
                + "const target = Array.from(el.options).find(o => (o.textContent || '').trim() === " + esc(label) + "); "
                + "if (!target) return false; "
                + "el.value = target.value; "
                + "el.dispatchEvent(new Event('change', {bubbles: true})); return true; })()");
    }

    /** 按索引选中（0 起）并派发 change 事件。 */
    public boolean selectByIndex(int index) throws IOException, TimeoutException {
        return page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el || !el.options || " + index + " < 0 || " + index + " >= el.options.length) return false; "
                + "el.selectedIndex = " + index + "; "
                + "el.dispatchEvent(new Event('change', {bubbles: true})); return true; })()");
    }

    /** 滚动元素到视口中央。 */
    public PageElement scrollIntoView() throws IOException, TimeoutException {
        page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return false; el.scrollIntoView({block:'center', inline:'center'}); return true; })()");
        return this;
    }

    /** 点击元素中心（真实鼠标事件，中心带 ±4px 随机偏移避免机器特征）。 */
    public void click() throws IOException, TimeoutException {
        JsonNode point = center();
        if (point == null) {
            throw new IOException("元素不存在或不可见: " + selector);
        }
        int x = point.path("x").asInt() + HumanDelay.clickOffset();
        int y = point.path("y").asInt() + HumanDelay.clickOffset();
        page.mouseClick(x, y);
    }

    /** 点击元素中心偏移 (offsetX, offsetY) 的位置（附加随机偏移）。 */
    public void clickAt(int offsetX, int offsetY) throws IOException, TimeoutException {
        JsonNode point = center();
        if (point == null) {
            throw new IOException("元素不存在或不可见: " + selector);
        }
        int x = point.path("x").asInt() + offsetX + HumanDelay.clickOffset();
        int y = point.path("y").asInt() + offsetY + HumanDelay.clickOffset();
        page.mouseClick(x, y);
    }

    /** 鼠标悬停到元素中心。 */
    public void hover() throws IOException, TimeoutException {
        JsonNode point = center();
        if (point == null) {
            throw new IOException("元素不存在或不可见: " + selector);
        }
        page.mouseMove(point.path("x").asInt(), point.path("y").asInt());
    }

    /** 聚焦元素。 */
    public PageElement focus() throws IOException, TimeoutException {
        page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return false; el.focus(); return true; })()");
        return this;
    }

    /** 输入文本（追加；先点击聚焦）。 */
    public void type(String text) throws IOException, TimeoutException {
        click();
        page.insertText(text);
    }

    /** 清空并设置值（focus + select + insertText，适配 input/textarea/contenteditable）。 */
    public void setValue(String value) throws IOException, TimeoutException {
        page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return false; el.focus(); "
                + "if (typeof el.select === 'function') el.select(); "
                + "if (typeof el.setSelectionRange === 'function') { const len = el.value ? el.value.length : 0; el.setSelectionRange(0, len); } "
                + "return true; })()");
        page.insertText(value == null ? "" : value);
    }

    /** 按下按键（Enter/Tab/Escape/Backspace/Delete/方向键等，取元素中心，无需聚焦）。 */
    public void press(String key) throws IOException, TimeoutException {
        page.pressKey(key);
    }

    /** 取元素文本（innerText，无元素返回 null）。 */
    public String getText() throws IOException, TimeoutException {
        return page.evalString("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "return el ? (el.innerText || el.textContent || '') : null; })()");
    }

    /** 取元素属性（无元素返回 null）。 */
    public String getAttribute(String name) throws IOException, TimeoutException {
        return page.evalString("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "return el ? el.getAttribute(" + esc(name) + ") : null; })()");
    }

    /** 取表单值（input/textarea/select 的 value）。 */
    public String getValue() throws IOException, TimeoutException {
        return page.evalString("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "return el ? el.value : null; })()");
    }

    /** 派发 JS 事件（如 'change' / 'input' / 'blur'）。 */
    public PageElement dispatchEvent(String type) throws IOException, TimeoutException {
        page.evalBool("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return false; el.dispatchEvent(new Event(" + esc(type) + ", {bubbles: true})); return true; })()");
        return this;
    }

    /** 计算元素中心坐标（先滚动到视口）。返回 {x, y, w, h}，不存在返回 null。 */
    JsonNode center() throws IOException, TimeoutException {
        return page.evaluate("(() => { const el = document.querySelector(" + esc(selector) + "); "
                + "if (!el) return null; "
                + "el.scrollIntoView({block:'center', inline:'center'}); "
                + "const r = el.getBoundingClientRect(); "
                + "if (r.width <= 0 || r.height <= 0) return null; "
                + "return {x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2), "
                + "w: Math.round(r.width), h: Math.round(r.height)}; })()");
    }

    private CdpSession session() {
        return page.session();
    }

    /** CSS 选择器 → 可见性判断 JS（供 ChromePage 批量使用）。 */
    static String visibleJs(String selector) {
        return "(() => { const el = document.querySelector(" + esc(selector) + "); if (!el) return false; "
                + "const r = el.getBoundingClientRect(); "
                + "if (r.width <= 0 || r.height <= 0) return false; "
                + "const s = getComputedStyle(el); "
                + "return s.display !== 'none' && s.visibility !== 'hidden'; })()";
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
