package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeHealthChecker;
import cn.net.rjnetwork.xianyu.chrome.core.ChromePortPool;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeProfileManager;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeSession;
import cn.net.rjnetwork.xianyu.chrome.network.ChromeNetwork;
import cn.net.rjnetwork.xianyu.chrome.page.ChromePage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 登录后深度探索（真实联调，需人工扫码）。
 *
 * <p>扫码登录成功后自动探索：
 * <ol>
 *   <li>登录后首页状态（用户区文本 / 登录态 cookie）</li>
 *   <li>五类查询（查公司/查老板/查风险/查文书/查关系）的结果页 DOM 结构与接口响应</li>
 *   <li>企业详情页真实 DOM 结构</li>
 * </ol>
 * 输出用于校准 social-sdk-riskbird 的查询解析（结果列表选择器 / API 通道关键字）。
 */
@Disabled("真实联调：需人工扫码登录，默认跳过")
class RiskbirdPostLoginProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990004L;

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(false);
        config.setMaxActiveProfiles(2);
        ChromePortPool pool = new ChromePortPool(config);
        ChromeSession session = new ChromeSession(config, pool);
        ChromeHealthChecker hc = new ChromeHealthChecker(config);
        ChromeProfileManager manager = new ChromeProfileManager(config, pool, session, hc);
        browser = new ChromeBrowser(manager, session);
        browser.cleanupOrphans();
    }

    @AfterAll
    static void teardown() {
        if (browser != null) {
            browser.stopAccount(ACCOUNT);
        }
    }

    @Test
    void postLoginProbe() throws Exception {
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);

        // ============ 1. 扫码登录 ============
        System.out.println("\n========== 1. 扫码登录 ==========");
        String qrUrl = driver.prepareQrLogin();
        System.out.println("[PROBE] 二维码: " + qrUrl);
        System.out.println("[PROBE] 请用微信/风鸟App 扫码（120 秒）...");
        RiskbirdLoginResult login = driver.waitQrLogin(null);
        System.out.println("[PROBE] 登录结果: " + login.isSuccess() + ", " + login.getMessage());
        if (!login.isSuccess()) {
            System.out.println("[PROBE] 未扫码，跳过探索");
            return;
        }
        System.out.println("[PROBE] 登录态 cookie: " + driver.extractCookieHeader());

        // ============ 2. 登录后首页状态 ============
        System.out.println("\n========== 2. 登录后首页状态 ==========");
        try (ChromePage page = browser.openPage(ACCOUNT)) {
            page.navigate("https://www.riskbird.com/");
            Thread.sleep(3000);
            System.out.println("[PROBE] URL: " + page.url());
            // 用户区文本（登录后应显示用户名/会员信息）
            String userText = page.evalString("(() => { "
                    + "const el = document.querySelector('[class*=userinfo], [class*=user-info], [class*=member]'); "
                    + "return el ? (el.innerText || '').trim().slice(0, 200) : '(未找到用户区)'; })()");
            System.out.println("[PROBE] 用户区文本: " + userText);
            // 是否存在会员/额度提示
            String body = page.evalString("(document.body.innerText || '').slice(0, 1500)");
            System.out.println("[PROBE] 首页文本片段: " + (body == null ? "null" : body.replace('\n', '|').substring(0, Math.min(800, body.length()))));
        }

        // ============ 3. 五类查询探索 ============
        System.out.println("\n========== 3. 五类查询探索 ==========");
        for (RiskbirdConfig.QueryType type : RiskbirdConfig.QueryType.values()) {
            System.out.println("\n---- " + type.label + " (" + type.path + ") ----");
            probeSearch(config, type, "阿里巴巴");
        }

        // ============ 4. 企业详情页探索 ============
        System.out.println("\n========== 4. 企业详情页探索 ==========");
        probeDetail(config);
    }

    /** 探索一次查询：导航 → dump 接口请求与响应 → dump 结果 DOM 结构。 */
    private void probeSearch(RiskbirdConfig config, RiskbirdConfig.QueryType type, String keyword) throws Exception {
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/search/" + type.path
                    + "?keyword=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                    + "&timestamp=" + System.currentTimeMillis();
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(5000); // 等异步 XHR + 渲染
                System.out.println("[PROBE] 结果页 URL: " + page.url());

                // 页面文本（判断是否命中/拦截/额度）
                String body = page.evalString("(document.body.innerText || '').slice(0, 1200)");
                System.out.println("[PROBE] 页面文本: " + (body == null ? "null" : body.replace('\n', '|').substring(0, Math.min(600, body.length()))));

                // 结果列表候选 DOM
                var items = page.evaluate("(() => { "
                        + "const sels = ['[class*=search-result] [class*=item]', '[class*=company-list] li', '[class*=ent-item]', '[class*=list] li', '[class*=result] a', 'table tbody tr']; "
                        + "const found = {}; "
                        + "for (const s of sels) { const n = document.querySelectorAll(s).length; if (n > 0) found[s] = n; } "
                        + "const sample = []; "
                        + "const first = document.querySelector(sels[0]) || document.querySelector('[class*=list] li'); "
                        + "if (first) { sample.push((first.innerText || '').trim().slice(0, 200)); } "
                        + "return {found, sample}; })()");
                System.out.println("[PROBE] 结果 DOM 候选: " + items);

                // 总数提示
                String totalText = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/共\\s*([\\d,]+)\\s*条|为您找到\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[PROBE] 总数提示: " + totalText);
            }

            // 接口响应（抓包）
            System.out.println("[PROBE] 相关接口:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                if (r.url != null && (r.url.contains("/search/") || r.url.contains("riskbird-api"))) {
                    String resp = net.body(r.requestId);
                    System.out.println("  " + r.method + " " + (r.url.length() > 150 ? r.url.substring(0, 150) : r.url)
                            + " → " + (resp == null ? "(无响应体)" : resp.length() + " chars"));
                    if (resp != null && resp.contains("company")) {
                        System.out.println("    body 前 300: " + resp.substring(0, Math.min(300, resp.length())).replace('\n', ' '));
                    }
                    if (++shown >= 5) break;
                }
            }
        }
    }

    /** 探索详情页真实 DOM 结构。 */
    private void probeDetail(RiskbirdConfig config) throws Exception {
        try (ChromePage page = browser.openPage(ACCOUNT)) {
            String url = "https://www.riskbird.com/ent/" + java.net.URLEncoder.encode(
                    "北京石头世纪科技股份有限公司", java.nio.charset.StandardCharsets.UTF_8) + ".html?entid=";
            page.navigate(url);
            Thread.sleep(5000);
            System.out.println("[PROBE] 详情页 URL: " + page.url());
            System.out.println("[PROBE] 详情页 Title: " + page.title());

            String body = page.evalString("(document.body.innerText || '').slice(0, 1500)");
            System.out.println("[PROBE] 详情页文本: " + (body == null ? "null" : body.replace('\n', '|').substring(0, Math.min(700, body.length()))));

            // 详情关键信息行候选
            var rows = page.evaluate("(() => { "
                    + "const sels = ['[class*=base-info] tr', '[class*=detail] li', '[class*=info] tr', 'table tr']; "
                    + "const out = {}; "
                    + "for (const s of sels) { const n = document.querySelectorAll(s).length; if (n > 0) out[s] = n; } "
                    + "return out; })()");
            System.out.println("[PROBE] 详情 DOM 候选: " + rows);

            // 法定代表人/信用代码等关键字段候选
            String keys = page.evalString("(() => { "
                    + "const t = document.body.innerText || ''; "
                    + "const grab = (kw) => { const i = t.indexOf(kw); return i >= 0 ? t.substring(i, i + 60).replace(/\\s+/g, ' ').trim() : null; }; "
                    + "return JSON.stringify({法人: grab('法定代表人'), 信用代码: grab('统一社会信用代码'), 成立: grab('成立日期'), 资本: grab('注册资本'), 状态: grab('登记状态')}); })()");
            System.out.println("[PROBE] 详情关键字段: " + keys);
        }
    }
}
