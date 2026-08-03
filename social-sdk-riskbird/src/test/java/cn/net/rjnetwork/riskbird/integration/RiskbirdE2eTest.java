package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeHealthChecker;
import cn.net.rjnetwork.xianyu.chrome.core.ChromePortPool;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeProfileManager;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * riskbird 真实联调测试（真实 Chrome + 真实站点）。
 *
 * <p>自动部分（无需登录）：
 * <ul>
 *   <li>{@link #searchBlockedWhenNotLoggedIn()} — 未登录搜索应被拦截并返回明确提示</li>
 *   <li>{@link #queryCompanyDetail()} — 企业详情页 URL 可访问、可解析</li>
 * </ul>
 *
 * <p>半自动部分（需要你配合扫码）：
 * <ul>
 *   <li>{@link #qrLoginFlow()} — 打开二维码并打印 URL，等待你扫码（默认 120s），
 *       扫码成功后自动验证搜索/详情全链路</li>
 * </ul>
 */
@Disabled("真实联调：需本地 Chrome 与人工扫码，默认跳过")
class RiskbirdE2eTest {

    private static ChromeBrowser browser;
    private static ChromeProfileManager manager;
    private static final long E2E_ACCOUNT = 990002L;

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(false);
        config.setMaxActiveProfiles(2);
        ChromePortPool pool = new ChromePortPool(config);
        ChromeSession session = new ChromeSession(config, pool);
        ChromeHealthChecker hc = new ChromeHealthChecker(config);
        manager = new ChromeProfileManager(config, pool, session, hc);
        browser = new ChromeBrowser(manager, session);
        browser.cleanupOrphans();
    }

    @AfterAll
    static void teardown() {
        if (browser != null) {
            browser.stopAccount(E2E_ACCOUNT);
        }
    }

    private RiskbirdApiFacade newApi() {
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, E2E_ACCOUNT);
        return new RiskbirdApiFacade(config, driver);
    }

    @Test
    void searchBlockedWhenNotLoggedIn() throws Exception {
        RiskbirdApiFacade api = newApi();
        RiskbirdSearchResult result = api.search("阿里巴巴", 1);
        System.out.println("[E2E] 未登录搜索: success=" + result.isSuccess() + ", error=" + result.getError()
                + ", count=" + result.getCompanies().size());
        // 未登录时搜索被拦截：要么返回失败提示，要么 0 条结果
        assertFalse(result.isSuccess() && !result.getCompanies().isEmpty(),
                "未登录不应返回搜索结果（应被拦截）");
    }

    @Test
    void queryCompanyDetail() throws Exception {
        RiskbirdApiFacade api = newApi();
        RiskbirdCompany company = api.queryCompany("北京石头世纪科技股份有限公司");
        System.out.println("[E2E] 企业详情: " + company);
        // 详情页可访问（URL 有效）；字段可能因未登录而不全，但名称/URL 应可解析
        assertNotNull(company);
        assertNotNull(company.getDetailUrl());
        assertTrue(company.getDetailUrl().startsWith("https://www.riskbird.com/ent/"),
                "详情页 URL 应为 /ent/ 路径: " + company.getDetailUrl());
    }

    @Test
    void qrLoginFlow() throws Exception {
        RiskbirdApiFacade api = newApi();
        // 1. 打开二维码
        String qrUrl = api.prepareQrLogin();
        System.out.println("\n======================================================");
        System.out.println("[E2E] 请用微信或风鸟App 扫码登录（120 秒内）");
        System.out.println("[E2E] 二维码 URL: " + qrUrl);
        System.out.println("======================================================");
        assertNotNull(qrUrl);
        assertTrue(qrUrl.contains("createQrCode") || qrUrl.contains("qr"),
                "二维码 URL 应指向扫码接口: " + qrUrl);

        // 2. 轮询等待扫码（120s）
        RiskbirdLoginResult login = api.waitQrLogin(null);
        System.out.println("[E2E] 扫码登录结果: success=" + login.isSuccess() + ", msg=" + login.getMessage());
        if (!login.isSuccess()) {
            // 未扫码时提示，不失败（半自动测试，扫码是人工操作）
            System.out.println("[E2E] 未检测到扫码，跳过登录后验证（如需完整链路请重新运行并扫码）");
            return;
        }

        // 3. 登录后验证搜索（查公司）
        RiskbirdSearchResult search = api.search("阿里巴巴", 1);
        System.out.println("[E2E] 登录后搜索: success=" + search.isSuccess() + ", count=" + search.getCompanies().size()
                + ", error=" + search.getError());
        if (search.isSuccess() && !search.getCompanies().isEmpty()) {
            System.out.println("[E2E] 第一条结果: " + search.getCompanies().get(0).getName());
        }

        // 4. 提取登录态 Cookie（验证持久化复用）
        String cookie = api.extractCookieHeader();
        System.out.println("[E2E] 登录态 Cookie: " + (cookie == null ? "null" : cookie.length() + " chars"));
        assertNotNull(cookie);
        assertFalse(cookie.isBlank());
    }
}
