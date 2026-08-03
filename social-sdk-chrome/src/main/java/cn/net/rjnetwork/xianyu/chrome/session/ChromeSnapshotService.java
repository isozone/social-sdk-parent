package cn.net.rjnetwork.xianyu.chrome.session;

import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.chrome.model.SessionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 登录态快照服务：捕获 / 回放浏览器登录态（Cookie + localStorage + sessionStorage）。
 *
 * <p>非 Spring Bean，直接 {@code new ChromeSnapshotService(cdpSession)} 使用，随 {@link CdpSession} 生命周期失效。
 *
 * <p>典型用法：
 * <pre>{@code
 * // 导出（登录成功后备份）
 * try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
 *     SessionSnapshot snap = new ChromeSnapshotService(cdp).capture(accountId);
 *     /* 落盘 / 入库 *&#47;
 * }
 *
 * // 回放（容器重启后恢复登录态）
 * try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
 *     boolean ok = new ChromeSnapshotService(cdp).restore(snap);
 * }
 * }</pre>
 */
public class ChromeSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ChromeSnapshotService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 快照回放时先导航到此 URL（保证 cookie 域名上下文与 storage 归属正确）。 */
    private static final String GOOFISH_IM_URL = "https://www.goofish.com/";

    private final CdpSession session;

    public ChromeSnapshotService(CdpSession session) {
        this.session = session;
    }

    /**
     * 捕获当前浏览器上下文的登录态快照。
     *
     * @param accountId 归属账号
     */
    public SessionSnapshot capture(long accountId) throws IOException, TimeoutException {
        return capture(accountId, null);
    }

    /**
     * 捕获当前浏览器上下文的登录态快照。
     *
     * @param accountId 归属账号
     * @param sourceUrl 页面地址（可为 null，取当前 location.href）
     */
    public SessionSnapshot capture(long accountId, String sourceUrl) throws IOException, TimeoutException {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setCreatedAt(System.currentTimeMillis());
        snapshot.setSourceUrl(sourceUrl != null ? sourceUrl : evalString("location.href"));

        // 1. Cookie（走 CDP Network 域，能拿到 httpOnly）
        CdpCookieStore store = new CdpCookieStore(session);
        List<SessionSnapshot.CookieData> cookies = new ArrayList<>();
        for (CdpCookieStore.Cookie c : store.getAllCookies()) {
            cookies.add(SessionSnapshot.CookieData.builder()
                    .name(c.name)
                    .value(c.value)
                    .domain(c.domain)
                    .path(c.path)
                    .expires(c.expires)
                    .httpOnly(c.httpOnly)
                    .secure(c.secure)
                    .sameSite(c.sameSite)
                    .build());
        }
        snapshot.setCookies(cookies);

        // 2. localStorage（完整键值）
        snapshot.setLocalStorage(evalStorage("localStorage"));

        // 3. sessionStorage（完整键值）
        snapshot.setSessionStorage(evalStorage("sessionStorage"));

        log.info("[SNAPSHOT] 登录态快照已捕获, accountId={}, cookies={}, localStorage={}, sessionStorage={}",
                accountId, cookies.size(), snapshot.getLocalStorage().size(), snapshot.getSessionStorage().size());
        return snapshot;
    }

    /**
     * 回放登录态快照：先注入 Cookie，再写入 localStorage / sessionStorage。
     *
     * @return true = 全部成功；false = 部分失败（日志记录原因）
     */
    public boolean restore(SessionSnapshot snapshot) throws IOException, TimeoutException {
        if (snapshot == null) {
            return false;
        }
        boolean ok = true;

        // 1. 先导航到目标域，保证后续 storage 写入归属正确页面
        String target = snapshot.getSourceUrl() != null ? snapshot.getSourceUrl() : GOOFISH_IM_URL;
        try {
            navigate(target);
        } catch (Exception e) {
            log.warn("[SNAPSHOT] 回放前导航失败（忽略，继续注入）: {}", e.getMessage());
        }

        // 2. Cookie 注入
        if (snapshot.getCookies() != null && !snapshot.getCookies().isEmpty()) {
            try {
                List<Map<String, Object>> params = new ArrayList<>();
                for (SessionSnapshot.CookieData c : snapshot.getCookies()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", c.getName());
                    m.put("value", c.getValue() == null ? "" : c.getValue());
                    m.put("url", cookieUrl(c.getDomain()));
                    m.put("path", c.getPath() == null ? "/" : c.getPath());
                    if (c.getExpires() != null) {
                        m.put("expires", c.getExpires());
                    }
                    if (c.getHttpOnly() != null) {
                        m.put("httpOnly", c.getHttpOnly());
                    }
                    if (c.getSecure() != null) {
                        m.put("secure", c.getSecure());
                    }
                    if (c.getSameSite() != null) {
                        m.put("sameSite", c.getSameSite());
                    }
                    params.add(m);
                }
                new CdpCookieStore(session).setCookies(params);
            } catch (Exception e) {
                log.warn("[SNAPSHOT] Cookie 注入失败: {}", e.getMessage());
                ok = false;
            }
        }

        // 3. storage 写入
        ok &= writeStorage("localStorage", snapshot.getLocalStorage());
        ok &= writeStorage("sessionStorage", snapshot.getSessionStorage());

        log.info("[SNAPSHOT] 登录态快照回放完成, accountId={}, ok={}", snapshot.getAccountId(), ok);
        return ok;
    }

    /** 导出指定 storage 的全部键值（走 Runtime.evaluate 一次性取回）。 */
    private Map<String, String> evalStorage(String storageName) throws IOException, TimeoutException {
        JsonNode v = evaluate("(() => { const s = window." + storageName + "; const out = {}; "
                + "for (let i = 0; i < s.length; i++) { const k = s.key(i); out[k] = s.getItem(k); } return out; })()");
        Map<String, String> map = new LinkedHashMap<>();
        if (v != null && v.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = v.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText());
            }
        }
        return map;
    }

    /** 把快照中的 storage 键值写回页面。 */
    private boolean writeStorage(String storageName, Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return true;
        }
        try {
            StringBuilder js = new StringBuilder("(() => { const s = window." + storageName + ";");
            js.append(" try { s.clear(); } catch (e) {}");
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                js.append(" s.setItem(").append(JSON.valueToTree(e.getKey()))
                        .append(", ").append(JSON.valueToTree(e.getValue() == null ? "" : e.getValue())).append(");");
            }
            js.append(" return true; })()");
            JsonNode v = evaluate(js.toString());
            return v != null && v.isBoolean() && v.asBoolean();
        } catch (Exception e) {
            log.warn("[SNAPSHOT] {} 写入失败: {}", storageName, e.getMessage());
            return false;
        }
    }

    private JsonNode evaluate(String expression) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("expression", expression);
        params.put("returnByValue", true);
        params.put("awaitPromise", true);
        JsonNode result = session.send("Runtime.evaluate", params);
        JsonNode exc = result.get("exceptionDetails");
        if (exc != null && !exc.isNull()) {
            throw new IOException("页面脚本执行异常: " + exc.path("text").asText());
        }
        return result.path("result").get("value");
    }

    private String evalString(String expression) throws IOException, TimeoutException {
        JsonNode v = evaluate(expression);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** 导航到 URL（不等待加载完成，注入场景只需域名上下文）。 */
    private void navigate(String url) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("url", url);
        session.send("Page.navigate", params);
    }

    /** Cookie domain → 注入用的 url（Network.setCookies 需要 url 或 domain，这里补全 scheme）。 */
    private static String cookieUrl(String domain) {
        if (domain == null || domain.isBlank()) {
            return "https://www.goofish.com/";
        }
        String d = domain.startsWith(".") ? domain.substring(1) : domain;
        return "https://" + d + "/";
    }
}
