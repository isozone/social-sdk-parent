package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
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

/**
 * 登录态查询解析校准探索（真实 Cookie 联调，免扫码）。
 *
 * <p>目标：拿到五类查询的真实接口 URL/响应结构、结果 DOM 选择器、详情页 DOM 结构，
 * 用于校准 {@link RiskbirdConfig} 的 searchApiUrlKeywords / searchResultItemSelector / detailRowSelector
 * 与 {@link ChromeRiskbirdDriver} 的 API 匹配 / DOM 解析逻辑。
 *
 * <p>运行：{@code mvn -pl social-sdk-riskbird test -Dtest=RiskbirdCalibrationProbeTest -DfailIfNoTests=false -Drb.cookie="..."}
 */
@Disabled("真实联调：需 -Drb.cookie 提供已登录 Cookie，默认跳过")
class RiskbirdCalibrationProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990006L;
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
    void calibrateQueries() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[CAL] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);

        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[CAL] Cookie 登录: success=" + login.isSuccess() + ", msg=" + login.getMessage());
        if (!login.isSuccess()) {
            return;
        }

        // ============ 1. 查公司：拿真实结果 + entId ============
        System.out.println("\n========== 1. 查公司 ==========");
        RiskbirdSearchResult companyResult = api.search(RiskbirdConfig.QueryType.COMPANY, "阿里巴巴", 1);
        System.out.println("[CAL] 查公司: success=" + companyResult.isSuccess()
                + ", count=" + companyResult.getCompanies().size() + ", total=" + companyResult.getTotal()
                + ", channel=" + companyResult.getChannel());
        String entId = null;
        if (companyResult.isSuccess() && !companyResult.getCompanies().isEmpty()) {
            var c0 = companyResult.getCompanies().get(0);
            entId = c0.getEntId();
            System.out.println("[CAL] 首条: " + c0.getName() + " | entId=" + entId);
        }

        // ============ 2. 详情页（用真实 entId）============
        System.out.println("\n========== 2. 详情页（entId=" + entId + "）==========");
        probeDetail(entId);

        // ============ 3. 五类查询接口 + DOM dump ============
        System.out.println("\n========== 3. 五类查询接口/DOM dump ==========");
        for (RiskbirdConfig.QueryType type : RiskbirdConfig.QueryType.values()) {
            System.out.println("\n---- " + type.label + " (" + type.path + ") ----");
            probeSearchRaw(type, "阿里巴巴");
        }
    }

    /** 原始探索：抓接口 + dump 结果 DOM。 */
    private void probeSearchRaw(RiskbirdConfig.QueryType type, String keyword) throws Exception {
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/search/" + type.path + "?keyword="
                    + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                    + "&timestamp=" + System.currentTimeMillis();
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
                // 接口请求（所有含 riskbird-api / search / query 的）
                int shown = 0;
                for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                    String u = r.url == null ? "" : r.url;
                    if (u.contains("riskbird-api") || u.contains("/search") || u.contains("query")
                            || u.contains("company") || u.contains("boss") || u.contains("risk")
                            || u.contains("wenshu") || u.contains("relation")) {
                        String resp = net.body(r.requestId);
                        System.out.println("[CAL]   API " + r.method + " " + (u.length() > 140 ? u.substring(0, 140) : u)
                                + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                        if (resp != null && resp.length() < 400) {
                            System.out.println("[CAL]     body: " + resp.replace('\n', ' '));
                        }
                        if (++shown >= 4) break;
                    }
                }
                // 结果 DOM
                String totalText = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/为您找到\\s*([\\d,]+)\\s*条|共\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[CAL]   总数: " + totalText);
                var cards = page.evaluate("(() => { "
                        + "const sels = ['[class*=result-list] [class*=item]', '[class*=company-list] li', "
                        + "'[class*=card]', '[class*=ent-item]', 'tbody tr', '[class*=list-item]', '[class*=search-list] [class*=item]']; "
                        + "const out = {}; "
                        + "for (const s of sels) { const n = document.querySelectorAll(s).length; if (n > 0) out[s] = n; } "
                        + "const sample = (() => { "
                        + "  for (const s of sels) { const el = document.querySelector(s); "
                        + "    if (el) { const t = (el.innerText||'').trim().slice(0,150); if (t && !t.includes('扫码下载')) return {s, text: t}; } } "
                        + "  return null; })(); "
                        + "return {found: out, sample}; })()");
                System.out.println("[CAL]   结果DOM: " + cards);
            }
        }
    }

    /** 详情页结构探索（用真实 entId）。 */
    private void probeDetail(String entId) throws Exception {
        try (ChromePage page = browser.openPage(ACCOUNT)) {
            String company = java.net.URLEncoder.encode("阿里巴巴（中国）有限公司", java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://www.riskbird.com/ent/" + company + ".html?entid=" + (entId == null ? "" : entId);
            page.navigate(url);
            Thread.sleep(6000);
            System.out.println("[CAL] 详情页 URL: " + page.url());
            System.out.println("[CAL] 详情页 Title: " + page.title());

            String body = page.evalString("(document.body.innerText || '').slice(0, 2000)");
            System.out.println("[CAL] 详情文本: " + (body == null ? "null"
                    : body.replace('\n', '|').substring(0, Math.min(1000, body.length()))));

            // 关键字段
            String keys = page.evalString("(() => { "
                    + "const t = document.body.innerText || ''; "
                    + "const grab = (kw) => { const i = t.indexOf(kw); return i >= 0 ? t.substring(i, i + 50).replace(/\\s+/g, ' ').trim() : null; }; "
                    + "return JSON.stringify({法人: grab('法定代表人'), 信用代码: grab('统一社会信用代码'), "
                    + "成立: grab('成立日期'), 资本: grab('注册资本'), 状态: grab('登记状态'), 地址: grab('注册地址')}); })()");
            System.out.println("[CAL] 详情关键字段: " + keys);

            // 详情行 DOM 候选
            var rows = page.evaluate("(() => { "
                    + "const sels = ['[class*=base-info] tr', '[class*=base-info] li', '[class*=info] tr', "
                    + "'[class*=info-item]', '[class*=detail] tr', '[class*=basic] tr', '[class*=company-info] li']; "
                    + "const out = {}; "
                    + "for (const s of sels) { const n = document.querySelectorAll(s).length; if (n > 0) out[s] = n; } "
                    + "const sample = (() => { for (const s of sels) { const el = document.querySelector(s); "
                    + "  if (el) { const t = (el.innerText||'').trim().slice(0,120); if (t) return {s, text: t}; } } return null; })(); "
                    + "return {found: out, sample}; })()");
            System.out.println("[CAL] 详情DOM: " + rows);
        }
    }
}
