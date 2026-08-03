package cn.net.rjnetwork.xianyu.chrome.captcha;

import cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult;
import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 验证码自动化监测器：监测页面上是否出现滑块验证码，出现后自动调用
 * {@link XianyuCaptchaSolver} 完成「提取登录 cookie → 注入 → 触发验证 → 拖动滑块 → 回填新 cookie」全链路。
 *
 * <p>典型用法（一次性等待并求解）：
 * <pre>{@code
 * try (CaptchaWatcher watcher = chromeBrowser.watchCaptcha(accountId, solver)) {
 *     CaptchaResult result = watcher.waitAndSolve(punishUrl, 60_000, 1_000);
 *     if (result.isSuccess()) { /* 拿到新 x5sec *&#47; }
 * }
 * }</pre>
 *
 * <p>典型用法（后台常驻监测，出现验证码自动处理）：
 * <pre>{@code
 * CaptchaWatcher watcher = chromeBrowser.watchCaptcha(accountId, solver);
 * watcher.startWatching(punishUrl, 2_000, result -> {
 *     if (result.isLoginExpired()) { /* 推送网页端重新登录 *&#47; }
 * });
 * }</pre>
 *
 * <p>验证码出现判定使用可配置的 JS 表达式（默认覆盖阿里系滑块容器类名与验证码 iframe），
 * 业务方可按页面结构调整 {@code detectExpression}。
 */
public class CaptchaWatcher implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CaptchaWatcher.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 默认验证码出现检测表达式（阿里系滑块 + 验证码 iframe 通用特征）。 */
    public static final String DEFAULT_DETECT_EXPRESSION =
            "(() => {"
                    + "  const sels = ["
                    + "    '.nc_wrapper', '.nc-container', '.nc_iconfont', '.btn_slide',"
                    + "    '#nc_1_n1z', '#nc_1__scale_text', '#nc_1__scale_text2',"
                    + "    '.captcha-slider', '.captcha_wrap',"
                    + "    'iframe[src*=\"captcha\"]', 'iframe[src*=\"slide\"]',"
                    + "    'iframe[src*=\"punish\"]', 'iframe[src*=\"verify\"]',"
                    + "    '[class*=\"captcha\"]', '[class*=\"verify-slider\"]'"
                    + "  ];"
                    + "  for (const s of sels) { if (document.querySelector(s)) return true; }"
                    + "  return false;"
                    + "})()";

    private final CdpSession session;
    private final XianyuCaptchaSolver solver;
    private final String cdpHttpEndpoint;
    private final long fingerprintSeed;
    private final String detectExpression;

    private volatile boolean watching;
    private Thread watcherThread;

    public CaptchaWatcher(CdpSession session, XianyuCaptchaSolver solver,
                          String cdpHttpEndpoint, long fingerprintSeed) {
        this(session, solver, cdpHttpEndpoint, fingerprintSeed, DEFAULT_DETECT_EXPRESSION);
    }

    public CaptchaWatcher(CdpSession session, XianyuCaptchaSolver solver,
                          String cdpHttpEndpoint, long fingerprintSeed, String detectExpression) {
        this.session = session;
        this.solver = solver;
        this.cdpHttpEndpoint = cdpHttpEndpoint;
        this.fingerprintSeed = fingerprintSeed;
        this.detectExpression = detectExpression == null || detectExpression.isBlank()
                ? DEFAULT_DETECT_EXPRESSION : detectExpression;
    }

    // ==================== 检测 ====================

    /** 页面上是否出现验证码（走 {@code Runtime.evaluate} 执行检测表达式）。 */
    public boolean isCaptchaPresent() throws IOException, TimeoutException {
        if (!session.isOpen()) {
            return false;
        }
        ObjectNode params = JSON.createObjectNode();
        params.put("expression", detectExpression);
        params.put("returnByValue", true);
        JsonNode result = session.send("Runtime.evaluate", params);
        JsonNode v = result.path("result").path("value");
        return v.isBoolean() && v.asBoolean();
    }

    // ==================== 一次性求解 ====================

    /**
     * 等待验证码出现并自动求解。
     *
     * @param punishUrl        闲鱼风控 punish URL
     * @param detectTimeoutMs  等待验证码出现的总超时；超时返回 {@code CaptchaResult.fail("等待验证码出现超时")}
     * @param pollIntervalMs   检测轮询间隔
     * @return 验证码处理结果（可能 {@code success=false}，或 {@code loginExpired=true}）
     */
    public CaptchaResult waitAndSolve(String punishUrl, long detectTimeoutMs, long pollIntervalMs)
            throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + detectTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isCaptchaPresent()) {
                log.info("[WATCHER] 检测到验证码，开始求解, punishUrl={}", punishUrl);
                return solveOnce(punishUrl);
            }
            Thread.sleep(Math.max(200, pollIntervalMs));
        }
        return CaptchaResult.fail("等待验证码出现超时 (" + detectTimeoutMs + "ms)");
    }

    /**
     * 立即执行一次滑块求解（不等待验证码出现）。
     * 从当前浏览器上下文提取 goofish / taobao / alicdn 域 cookie，注入后走滑块链路，
     * 成功后新 x5sec cookie 已落回浏览器，可直接复用。
     */
    public CaptchaResult solveOnce(String punishUrl) {
        try {
            String loginCookieHeader = extractLoginCookieHeader();
            CaptchaResult result = solver.solve(punishUrl, cdpHttpEndpoint, fingerprintSeed, loginCookieHeader, null);
            log.info("[WATCHER] 滑块求解完成, success={}, loginExpired={}, msg={}",
                    result.isSuccess(), result.isLoginExpired(), result.getMessage());
            return result;
        } catch (Exception e) {
            log.warn("[WATCHER] 滑块求解异常: {}", e.getMessage());
            return CaptchaResult.fail("CaptchaWatcher 求解异常: " + e.getMessage());
        }
    }

    // ==================== 后台常驻监测 ====================

    /**
     * 启动后台监测线程：按 {@code pollIntervalMs} 轮询页面，出现验证码即自动调用
     * {@link #solveOnce(String)}，结果回调 {@code handler}（可为 null）。
     *
     * <p>重复调用幂等（已有监测线程时忽略）。调用 {@link #stopWatching()} 停止。
     */
    public synchronized void startWatching(String punishUrl, long pollIntervalMs, Consumer<CaptchaResult> handler) {
        if (watching) {
            log.debug("[WATCHER] 监测线程已在运行，忽略重复启动");
            return;
        }
        watching = true;
        watcherThread = new Thread(() -> {
            log.info("[WATCHER] 验证码监测线程启动, punishUrl={}, pollInterval={}ms", punishUrl, pollIntervalMs);
            while (watching && session.isOpen()) {
                try {
                    if (isCaptchaPresent()) {
                        log.info("[WATCHER] 检测到验证码，自动求解, punishUrl={}", punishUrl);
                        CaptchaResult result = solveOnce(punishUrl);
                        if (handler != null) {
                            try {
                                handler.accept(result);
                            } catch (Exception e) {
                                log.warn("[WATCHER] 回调异常: {}", e.getMessage());
                            }
                        }
                    }
                    Thread.sleep(Math.max(200, pollIntervalMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("[WATCHER] 监测轮询异常: {}", e.getMessage());
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("[WATCHER] 验证码监测线程退出");
        }, "captcha-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    /** 停止后台监测线程。 */
    public synchronized void stopWatching() {
        watching = false;
        Thread t = watcherThread;
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
        watcherThread = null;
    }

    public boolean isWatching() {
        return watching;
    }

    // ==================== 内部 ====================

    /** 从浏览器提取登录域 cookie（goofish / taobao / alicdn），拼成 cookie header。 */
    private String extractLoginCookieHeader() throws IOException, TimeoutException {
        CdpCookieStore store = new CdpCookieStore(session);
        List<CdpCookieStore.Cookie> cookies = new ArrayList<>();
        for (String url : List.of(
                "https://www.goofish.com/",
                "https://www.taobao.com/",
                "https://login.taobao.com/",
                "https://www.alicdn.com/")) {
            try {
                cookies.addAll(store.getCookies(url));
            } catch (Exception e) {
                log.debug("[WATCHER] 提取 cookie 失败, url={}, err={}", url, e.getMessage());
            }
        }
        String header = CdpCookieStore.toHeaderValue(cookies);
        log.debug("[WATCHER] 提取登录 cookie, count={}, headerLen={}", cookies.size(), header.length());
        return header;
    }

    @Override
    public void close() {
        stopWatching();
    }
}
