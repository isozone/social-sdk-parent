package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Cookie 注入联调测试（无需扫码）。
 *
 * <p>使用已登录的 riskbird Cookie（VIP 账号）直接注入容器，验证：
 * <ol>
 *   <li>{@code isLoggedIn()} 正确识别真实登录态（回归：app-uuid 误判 bug）</li>
 *   <li>五类查询（查公司/查老板/查风险/查文书/查关系）在登录态下真实可用</li>
 *   <li>dump 真实结果 DOM 与接口响应 → 校准结果列表选择器 / API 通道</li>
 *   <li>企业详情页真实结构</li>
 * </ol>
 *
 * <p>运行：{@code mvn -pl social-sdk-riskbird test -Dtest=RiskbirdCookieLoginTest -DfailIfNoTests=false -Drb.cookie="k1=v1; k2=v2"}
 */
@Disabled("真实联调：需 -Drb.cookie 提供已登录 Cookie，默认跳过")
class RiskbirdCookieLoginTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990005L;
    private static final String RB_COOKIE = System.getProperty("rb.cookie", "");

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
    void cookieLogin_realQueries() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[COOKIE] 未提供 -Drb.cookie，跳过（需已登录 Cookie）");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);

        // ============ 1. Cookie 注入登录 ============
        System.out.println("\n========== 1. Cookie 注入登录 ==========");
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[COOKIE] 登录: success=" + login.isSuccess() + ", msg=" + login.getMessage());
        assertTrue(login.isSuccess(), "Cookie 注入后应识别为已登录: " + login.getMessage());
        assertTrue(api.isLoggedIn(), "isLoggedIn() 应识别真实登录态（回归：app-uuid 误判）");

        // ============ 2. 五类查询 ============
        System.out.println("\n========== 2. 五类查询 ==========");
        for (RiskbirdConfig.QueryType type : RiskbirdConfig.QueryType.values()) {
            RiskbirdSearchResult r = api.search(type, "阿里巴巴", 1);
            System.out.println("[COOKIE] " + type.label + ": success=" + r.isSuccess()
                    + ", count=" + r.getCompanies().size() + ", total=" + r.getTotal()
                    + ", error=" + r.getError() + ", channel=" + r.getChannel());
            if (r.isSuccess() && !r.getCompanies().isEmpty()) {
                RiskbirdCompany c0 = r.getCompanies().get(0);
                System.out.println("[COOKIE]   首条: " + c0.getName() + " | entId=" + c0.getEntId()
                        + " | 法人=" + c0.getLegalPerson() + " | URL=" + c0.getDetailUrl());
            }
        }

        // ============ 3. 查公司：dump 真实结果 DOM 与接口 ============
        System.out.println("\n========== 3. 查公司真实结果结构 dump ==========");
        probeCompanyResultStructure(config);

        // ============ 4. 企业详情 ============
        System.out.println("\n========== 4. 企业详情 ==========");
        RiskbirdCompany detail = api.queryCompany("北京石头世纪科技股份有限公司");
        System.out.println("[COOKIE] 详情: " + detail);
        assertNotNull(detail.getDetailUrl());
    }

    /** 查公司结果页：dump 真实结果项 DOM 结构 + 抓包接口，用于校准选择器。 */
    private void probeCompanyResultStructure(RiskbirdConfig config) throws Exception {
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/search/company?keyword="
                    + java.net.URLEncoder.encode("阿里巴巴", java.nio.charset.StandardCharsets.UTF_8)
                    + "&timestamp=" + System.currentTimeMillis();
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
                System.out.println("[COOKIE] 结果页 URL: " + page.url());

                String body = page.evalString("(document.body.innerText || '').slice(0, 1500)");
                System.out.println("[COOKIE] 页面文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(900, body.length()))));

                // 结果项 DOM 结构（各种候选选择器的命中数与首条 innerText）
                var items = page.evaluate("(() => { "
                        + "const sels = ['[class*=search-result] [class*=item]', '[class*=company-list] li', "
                        + "'[class*=ent-item]', '[class*=result-list] li', '[class*=list] li', "
                        + "'[class*=company] a', '[class*=card]', 'tbody tr']; "
                        + "const found = {}; "
                        + "for (const s of sels) { const n = document.querySelectorAll(s).length; if (n > 0) found[s] = n; } "
                        + "const samples = []; "
                        + "for (const s of sels) { const el = document.querySelector(s); "
                        + "  if (el) { samples.push({s, text: (el.innerText||'').trim().slice(0,120)}); break; } } "
                        + "return {found, samples}; })()");
                System.out.println("[COOKIE] 结果 DOM 候选: " + items);

                // 总数
                String totalText = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/共\\s*([\\d,]+)\\s*条|为您找到\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[COOKIE] 总数提示: " + totalText);
            }

            // 接口抓包
            System.out.println("[COOKIE] 相关接口:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                if (r.url != null && (r.url.contains("/search/") || r.url.contains("riskbird-api"))) {
                    String resp = net.body(r.requestId);
                    System.out.println("  " + r.method + " " + (r.url.length() > 130 ? r.url.substring(0, 130) : r.url)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                    if (resp != null && resp.length() < 500) {
                        System.out.println("    body: " + resp.replace('\n', ' '));
                    }
                    if (++shown >= 6) break;
                }
            }
        }
    }
}
