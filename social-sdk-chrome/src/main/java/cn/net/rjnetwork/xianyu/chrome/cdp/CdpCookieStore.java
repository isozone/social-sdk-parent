package cn.net.rjnetwork.xianyu.chrome.cdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Cookie 操作封装（基于 CDP Network 域）。
 *
 * <p>能力：
 * <ul>
 *   <li>{@link #getAllCookies()} / {@link #getCookies(String...)} — 提取浏览器 / 指定 URL 的 cookie</li>
 *   <li>{@link #setCookie}/{@link #setCookies} — 注入登录态</li>
 *   <li>{@link #deleteCookie}/{@link #clearBrowserCookies} — 清理</li>
 *   <li>{@link #toHeaderValue(String)} — 拼成 {@code k1=v1; k2=v2} cookie 头，供 HTTP API 直连复用登录态</li>
 * </ul>
 *
 * <p>非持久化封装（每次调用走会话命令，无独立生命周期，随 {@link CdpSession} 关闭而失效）。
 */
public class CdpCookieStore {

    /** Cookie 描述（不可变）。 */
    public static final class Cookie {
        public final String name;
        public final String value;
        public final String domain;
        public final String path;
        public final Double expires;
        public final long size;
        public final boolean httpOnly;
        public final boolean secure;
        public final boolean session;
        public final String sameSite;
        public final String priority;

        private Cookie(String name, String value, String domain, String path, Double expires,
                       long size, boolean httpOnly, boolean secure, boolean session,
                       String sameSite, String priority) {
            this.name = name;
            this.value = value;
            this.domain = domain;
            this.path = path;
            this.expires = expires;
            this.size = size;
            this.httpOnly = httpOnly;
            this.secure = secure;
            this.session = session;
            this.sameSite = sameSite;
            this.priority = priority;
        }

        static Cookie from(JsonNode c) {
            return new Cookie(
                    c.path("name").asText(),
                    c.path("value").asText(),
                    c.path("domain").asText(),
                    c.path("path").asText(),
                    c.path("expires").isNumber() ? c.path("expires").asDouble() : null,
                    c.path("size").asLong(),
                    c.path("httpOnly").asBoolean(),
                    c.path("secure").asBoolean(),
                    c.path("session").asBoolean(),
                    c.path("sameSite").asText(null),
                    c.path("priority").asText(null));
        }

        @Override
        public String toString() {
            return String.format("Cookie{name='%s', domain='%s', path='%s', secure=%s, httpOnly=%s}",
                    name, domain, path, secure, httpOnly);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CdpSession session;

    public CdpCookieStore(CdpSession session) {
        this.session = session;
    }

    /** 提取浏览器上下文全部 cookie。 */
    public List<Cookie> getAllCookies() throws IOException, TimeoutException {
        JsonNode result = session.send("Network.getAllCookies", null);
        return parseCookies(result.path("cookies"));
    }

    /** 提取指定 URL（可多个）的 cookie。 */
    public List<Cookie> getCookies(String... urls) throws IOException, TimeoutException {
        ArrayNode arr = JSON.createArrayNode();
        for (String url : urls) {
            arr.add(url);
        }
        ObjectNode params = JSON.createObjectNode();
        params.set("urls", arr);
        JsonNode result = session.send("Network.getCookies", params);
        return parseCookies(result.path("cookies"));
    }

    /** 设置单个 cookie（返回是否成功）。 */
    public boolean setCookie(String name, String value, String url) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", name);
        params.put("value", value == null ? "" : value);
        params.put("url", url);
        JsonNode result = session.send("Network.setCookie", params);
        return result.path("success").asBoolean(false);
    }

    /** 设置单个 cookie（完整参数，可含 domain/path/expires/httpOnly/secure/sameSite 等）。 */
    public boolean setCookie(Map<String, Object> cookie) throws IOException, TimeoutException {
        JsonNode result = session.send("Network.setCookie", toParams(cookie));
        return result.path("success").asBoolean(false);
    }

    /** 批量设置 cookie。 */
    public void setCookies(List<Map<String, Object>> cookies) throws IOException, TimeoutException {
        ArrayNode arr = JSON.createArrayNode();
        for (Map<String, Object> cookie : cookies) {
            arr.add(toParams(cookie));
        }
        ObjectNode params = JSON.createObjectNode();
        params.set("cookies", arr);
        session.send("Network.setCookies", params);
    }

    /** 删除指定 cookie。 */
    public void deleteCookie(String name, String url) throws IOException, TimeoutException {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", name);
        params.put("url", url);
        session.send("Network.deleteCookies", params);
    }

    /** 清空浏览器上下文全部 cookie。 */
    public void clearBrowserCookies() throws IOException, TimeoutException {
        session.send("Network.clearBrowserCookies", null);
    }

    /**
     * 把匹配指定 URL 的 cookie 拼成 HTTP header 形式（{@code k1=v1; k2=v2}）。
     * 用于在非浏览器 HTTP 调用中复用浏览器登录态。
     */
    public String toHeaderValue(String url) throws IOException, TimeoutException {
        return toHeaderValue(getCookies(url));
    }

    /** 把 cookie 列表拼成 header 形式。 */
    public static String toHeaderValue(List<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            if (c == null || c.name == null || c.name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(c.name).append('=').append(c.value == null ? "" : c.value);
        }
        return sb.toString();
    }

    private static List<Cookie> parseCookies(JsonNode cookies) {
        List<Cookie> list = new ArrayList<>();
        if (cookies != null && cookies.isArray()) {
            for (JsonNode c : cookies) {
                list.add(Cookie.from(c));
            }
        }
        return list;
    }

    private static ObjectNode toParams(Map<String, Object> map) {
        ObjectNode node = JSON.createObjectNode();
        if (map != null) {
            map.forEach((k, v) -> node.set(k, JSON.valueToTree(v)));
        }
        return node;
    }
}
