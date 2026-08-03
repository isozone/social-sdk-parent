package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.chrome.captcha.CaptchaWatcher;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import cn.net.rjnetwork.xianyu.chrome.fingerprint.ChromeFingerprintEnhancer;
import cn.net.rjnetwork.xianyu.chrome.fingerprint.FingerprintVerifier;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.chrome.monitor.ChromeMonitor;
import cn.net.rjnetwork.xianyu.chrome.network.ChromeNetwork;
import cn.net.rjnetwork.xianyu.chrome.page.ChromePage;
import cn.net.rjnetwork.xianyu.chrome.session.ChromeSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 高级操作门面：把 social-sdk-chrome 的容器管理能力与高级 CDP 封装暴露给业务层。
 *
 * <p>典型用法：
 * <pre>{@code
 * // 1. 打开账号页面并执行真实操作
 * try (ChromePage page = chromeBrowser.openPage(accountId)) {
 *     page.navigate("https://www.goofish.com/");
 *     page.waitForSelector(".login-entry", 10_000).click();
 *     page.type("input[name=username]", "手机号");
 *     String title = page.title();
 *     byte[] png = page.screenshotPng();
 * }
 *
 * // 2. 提取登录态 cookie 供 HTTP API 复用
 * try (CdpSession session = chromeBrowser.connectToAccount(accountId)) {
 *     String header = new CdpCookieStore(session).toHeaderValue("https://www.goofish.com/");
 * }
 *
 * // 3. 监听/拦截网络请求
 * try (CdpSession session = chromeBrowser.connectToAccount(accountId)) {
 *     ChromeNetwork net = new ChromeNetwork(session);
 *     net.enable();
 *     net.onRequest(r -> log.info("req: {}", r.url));
 *     net.interceptAll();
 * }
 * }</pre>
 */
@Component
public class ChromeBrowser {

    private static final Logger log = LoggerFactory.getLogger(ChromeBrowser.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChromeProfileManager profileManager;
    private final ChromeSession session;
    private final OkHttpClient httpClient;

    /** 可选注入的滑块验证码求解器（cdp-auth 模块未引入时可为 null，watchCaptcha 会提示）。 */
    private XianyuCaptchaSolver captchaSolver;

    @Autowired
    public ChromeBrowser(ChromeProfileManager profileManager, ChromeSession session) {
        this.profileManager = profileManager;
        this.session = session;
        // 高级操作专用长连接客户端：pingInterval 保活，避免空闲被对端断开
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    @Autowired(required = false)
    public void setCaptchaSolver(XianyuCaptchaSolver captchaSolver) {
        this.captchaSolver = captchaSolver;
    }

    // ==================== 容器获取 ====================

    /**
     * 获取账号容器；容器不存在或已崩溃时自动启动（带代理绑定 + 指纹注入）。
     *
     * @throws ChromeException 启动失败 / 代理绑定失败
     */
    public ChromeProfile requireProfile(long accountId) {
        ChromeProfile profile = profileManager.getProfile(accountId).orElse(null);
        if (profile != null && profile.isAlive()) {
            return profile;
        }
        return profileManager.launchAccount(accountId, "account-" + accountId);
    }

    /**
     * 确保账号容器在运行（未启动则启动，可指定展示名）。
     */
    public ChromeProfile ensureAccount(long accountId, String accountName) {
        ChromeProfile profile = profileManager.getProfile(accountId).orElse(null);
        if (profile != null && profile.isAlive()) {
            return profile;
        }
        return profileManager.launchAccount(accountId, accountName);
    }

    /**
     * 停止账号容器并释放端口/代理。
     */
    public void stopAccount(long accountId) {
        profileManager.stopAccount(accountId);
    }

    // ==================== 多标签页 ====================

    /** 标签页描述。 */
    public static final class TabInfo {
        public final String targetId;
        public final String url;
        public final String title;

        TabInfo(String targetId, String url, String title) {
            this.targetId = targetId;
            this.url = url;
            this.title = title;
        }

        @Override
        public String toString() {
            return "TabInfo{id='" + targetId + "', url='" + url + "', title='" + title + "'}";
        }
    }

    /**
     * 列出账号容器当前所有 page 标签页（含空白页）。
     */
    public List<TabInfo> listTabs(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        JsonNode targets = session.listTargets(profile.getCdpPort());
        List<TabInfo> tabs = new ArrayList<>();
        if (targets != null && targets.isArray()) {
            for (JsonNode t : targets) {
                if ("page".equals(t.path("type").asText())) {
                    tabs.add(new TabInfo(
                            t.path("id").asText(),
                            t.path("url").asText(null),
                            t.path("title").asText(null)));
                }
            }
        }
        return tabs;
    }

    /**
     * 新开标签页并导航到 URL，返回 targetId。
     */
    public String openTab(long accountId, String url) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        String resp = session.createTarget(profile.getCdpPort(), url);
        if (resp == null || resp.isBlank()) {
            throw new IOException("创建标签页失败: 无响应, accountId=" + accountId);
        }
        JsonNode node = JSON.readTree(resp);
        String targetId = node.path("id").asText(null);
        if (targetId == null || targetId.isEmpty()) {
            throw new IOException("创建标签页失败: 响应无 targetId, resp=" + resp);
        }
        log.info("[TABS] 新标签页已打开, accountId={}, targetId={}, url={}", accountId, targetId, url);
        return targetId;
    }

    /**
     * 关闭指定标签页。
     */
    public void closeTab(long accountId, String targetId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        session.closeTarget(profile.getCdpPort(), targetId);
        log.info("[TABS] 标签页已关闭, accountId={}, targetId={}", accountId, targetId);
    }

    /**
     * 在指定标签页上建立 CDP 会话（该标签页的新入口）。
     */
    public CdpSession connectToTab(long accountId, String targetId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        JsonNode targets = session.listTargets(profile.getCdpPort());
        if (targets != null && targets.isArray()) {
            for (JsonNode t : targets) {
                if (targetId.equals(t.path("id").asText())) {
                    String wsUrl = t.path("webSocketDebuggerUrl").asText();
                    if (wsUrl != null && !wsUrl.isEmpty()) {
                        return CdpSession.connect(wsUrl, httpClient);
                    }
                }
            }
        }
        throw new IOException("未找到标签页: accountId=" + accountId + ", targetId=" + targetId);
    }

    // ==================== 高级操作入口 ====================

    /**
     * 打开账号页面的高级操作入口（{@link ChromePage}）。
     * 返回对象持有独立 CDP 会话，使用后应 {@link ChromePage#close()} 释放。
     */
    public ChromePage openPage(long accountId) throws IOException, TimeoutException {
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        String targetId = targetIdOf(profile.getCdpPort());
        return new ChromePage(cdp, targetId);
    }

    /**
     * 建立账号容器 page target 的持久 CDP 会话（无页面时自动新建空白页）。
     * 返回对象使用后应 {@link CdpSession#close()}。
     */
    public CdpSession connectToAccount(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        return connectToAccount(profile.getCdpPort());
    }

    /**
     * 建立指定 CDP 端口上 page target 的持久会话（无页面时自动新建空白页）。
     */
    public CdpSession connectToAccount(int cdpPort) throws IOException {
        String wsUrl = pageTargetWsUrl(cdpPort);
        if (wsUrl == null || wsUrl.isEmpty()) {
            session.createTarget(cdpPort, "about:blank");
            wsUrl = pageTargetWsUrl(cdpPort);
        }
        if (wsUrl == null || wsUrl.isEmpty()) {
            throw new IOException("未找到可用的 page target, cdpPort=" + cdpPort);
        }
        return CdpSession.connect(wsUrl, httpClient);
    }

    /**
     * 打开账号页面的运行时监控器（console / 异常 / 性能 / 崩溃现场快照）。
     * 返回对象持有独立 CDP 会话，使用后应 {@link ChromeMonitor#close()} 释放。
     */
    public ChromeMonitor monitorAccount(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        return new ChromeMonitor(cdp);
    }

    /**
     * 打开账号页面的网络抓包器（请求/响应/失败记录 + 响应体捕获 + Fetch 拦截）。
     * 返回对象持有独立 CDP 会话，使用后应 {@link ChromeNetwork#close()} 释放。
     */
    public ChromeNetwork networkAccount(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        return new ChromeNetwork(cdp);
    }

    /**
     * 打开账号页面的验证码自动化监测器（监测滑块弹窗 → 自动提取 cookie → 调用
     * {@link XianyuCaptchaSolver} 求解回填）。
     *
     * <p>需要 {@link XianyuCaptchaSolver} 已注入（social-sdk-cdp-auth 模块引入且扫描到
     * {@code cn.net.rjnetwork.xianyu.captcha} 包）；未注入时抛出 {@link ChromeException}。
     * 返回对象持有独立 CDP 会话，使用后应 {@link CaptchaWatcher#close()} 释放。
     */
    public CaptchaWatcher watchCaptcha(long accountId) throws IOException {
        if (captchaSolver == null) {
            throw new ChromeException("CAPTCHA_SOLVER_MISSING",
                    "未注入 XianyuCaptchaSolver：请引入 social-sdk-cdp-auth 依赖并确保扫描 cn.net.rjnetwork.xianyu.captcha 包");
        }
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        return new CaptchaWatcher(cdp, captchaSolver, profile.getCdpEndpoint(), profile.getSeed());
    }

    // ==================== 账号态持久化 ====================

    /**
     * 打开账号页面的登录态快照服务（Cookie + localStorage + sessionStorage 捕获/回放）。
     * 返回对象持有独立 CDP 会话，使用后应 {@link CdpSession#close()} 释放。
     */
    public ChromeSnapshotService snapshotAccount(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        return new ChromeSnapshotService(cdp);
    }

    /**
     * 尝试重连已运行但未纳入管理的 Chrome 容器（应用重启后保登录态复用）。
     *
     * @return true = 重连成功并纳入管理；false = 无端口标记或端口不可达
     */
    public boolean reattachAccount(long accountId) {
        return profileManager.reattachAccount(accountId);
    }

    /**
     * 孤儿清扫：清理残留端口标记与残留锁文件（应用启动时调用一次）。
     */
    public void cleanupOrphans() {
        profileManager.cleanupOrphans();
    }

    // ==================== 资源运维 ====================

    /**
     * 采集所有活跃容器的资源指标（PID / 状态 / 存活 / profile 磁盘占用）。
     * 供监控面板、告警、资源调度使用。
     */
    public List<ChromeProfileManager.ContainerMetric> collectContainerMetrics() {
        return profileManager.collectMetrics();
    }

    /**
     * 手动触发磁盘配额检查：user-data-dir 总大小超过 {@code chrome.disk-quota-mb}
     * 时按 LRU 回收容器释放磁盘。
     */
    public void enforceDiskQuota() {
        profileManager.enforceDiskQuota();
    }

    /**
     * 向账号页面注入增强反指纹脚本（时区/地理/字体/音频/设备参数等，双通道：
     * addScriptToEvaluateOnNewDocument 持久化 + Runtime.evaluate 立即生效）。
     *
     * <p>注意：脚本带防重入标记，重复调用不会二次 patch 原型链。
     */
    public void applyEnhancedFingerprint(long accountId) throws IOException, TimeoutException {
        ChromeProfile profile = requireProfile(accountId);
        long seed = profile.getSeed();
        String script = ChromeFingerprintEnhancer.buildEnhancedScript(seed, profile.getFingerprintOverride());
        try (CdpSession cdp = connectToAccount(profile.getCdpPort())) {
            ObjectNode add = JSON.createObjectNode();
            add.put("source", script);
            cdp.send("Page.addScriptToEvaluateOnNewDocument", add);
            ObjectNode ev = JSON.createObjectNode();
            ev.put("expression", script);
            ev.put("returnByValue", true);
            cdp.send("Runtime.evaluate", ev);
            log.info("[ENHANCED] 增强指纹已注入, accountId={}, seed={}, scriptLen={}", accountId, seed, script.length());
        }
    }

    /**
     * 指纹自检：回读页面指纹关键项，校验反检测注入是否生效。
     *
     * @return 全部检查项（含通过项）；调用方可用 {@link FingerprintVerifier#verifyLeaks()} 只取漏网项
     */
    public FingerprintVerifier verifyFingerprint(long accountId) throws IOException {
        ChromeProfile profile = requireProfile(accountId);
        CdpSession cdp = connectToAccount(profile.getCdpPort());
        return new FingerprintVerifier(cdp);
    }

    // ==================== 内部工具 ====================

    /** 查找端口上第一个 page target（无则返回 null）。 */
    private JsonNode findPageTarget(int cdpPort) throws IOException {
        JsonNode targets = session.listTargets(cdpPort);
        if (targets != null && targets.isArray()) {
            for (JsonNode t : targets) {
                if ("page".equals(t.path("type").asText())) {
                    return t;
                }
            }
        }
        return null;
    }

    /** page target 的 webSocketDebuggerUrl。 */
    private String pageTargetWsUrl(int cdpPort) throws IOException {
        JsonNode target = findPageTarget(cdpPort);
        return target != null ? target.path("webSocketDebuggerUrl").asText() : null;
    }

    /** page target id（供日志/调试展示）。 */
    private String targetIdOf(int cdpPort) throws IOException {
        JsonNode target = findPageTarget(cdpPort);
        return target != null ? target.path("id").asText() : null;
    }
}
