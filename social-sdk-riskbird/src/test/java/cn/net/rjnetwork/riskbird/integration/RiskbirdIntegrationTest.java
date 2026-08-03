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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * riskbird 真实环境集成测试（默认跳过，需真实扫码登录参与）。
 *
 * <p>真实站点（2026-08-03 实测）：
 * <ul>
 *   <li>登录以<b>扫码</b>为主（微信/风鸟App），无账号密码表单</li>
 *   <li>搜索 URL：{@code /search/company|boss|risk|wenshu|relation?keyword=&timestamp=}</li>
 *   <li>未登录/额度不足时搜索被拦截：「查询次数已达到上限」</li>
 * </ul>
 *
 * <p>启用方式：去掉 {@code @Disabled} 后运行，测试会打开浏览器二维码，
 * 请用微信或风鸟App 扫码（120 秒内）完成登录链路验证。
 */
@Disabled("需要真实 riskbird 扫码登录与本地 Chrome，默认跳过；本地手动启用后配合扫码")
class RiskbirdIntegrationTest {

    /** 手动构造最小容器管理链（无 Spring 环境）。 */
    private ChromeBrowser buildChromeBrowser() {
        ChromeConfig config = new ChromeConfig();
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(false);
        ChromePortPool pool = new ChromePortPool(config);
        ChromeSession session = new ChromeSession(config, pool);
        ChromeHealthChecker hc = new ChromeHealthChecker(config);
        ChromeProfileManager manager = new ChromeProfileManager(config, pool, session, hc);
        return new ChromeBrowser(manager, session);
    }

    @Test
    void qrLogin_thenFiveTypeQueries() throws Exception {
        ChromeBrowser browser = buildChromeBrowser();
        try {
            RiskbirdConfig config = new RiskbirdConfig();
            long accountId = 900001L; // 集成测试专用隔离账号
            ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, accountId);
            RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);

            // 1. 账号密码登录（真实站点无密码表单，应返回明确提示）
            RiskbirdLoginResult pwd = api.loginWithPassword("any", "any");
            assertFalse(pwd.isSuccess(), "真实站点无账号密码表单，应提示使用扫码");
            assertTrue(pwd.getMessage().contains("扫码"), "提示应指向扫码登录: " + pwd.getMessage());

            // 2. 扫码登录（打开二维码，等待人工扫码）
            String qrUrl = api.prepareQrLogin();
            System.out.println("[IT] 二维码: " + qrUrl);
            RiskbirdLoginResult login = api.waitQrLogin(null);
            System.out.println("[IT] 扫码结果: " + login.isSuccess() + ", " + login.getMessage());
            if (!login.isSuccess()) {
                System.out.println("[IT] 未扫码，跳过登录后验证");
                return;
            }
            assertTrue(api.isLoggedIn());

            // 3. 提取登录态 Cookie
            String cookie = api.extractCookieHeader();
            assertNotNull(cookie);
            assertFalse(cookie.isBlank());
            System.out.println("[IT] 登录态 Cookie: " + cookie.length() + " chars");

            // 4. 五类查询（查公司/查老板/查风险/查文书/查关系）
            for (RiskbirdConfig.QueryType type : RiskbirdConfig.QueryType.values()) {
                RiskbirdSearchResult result = api.search(type, "阿里巴巴", 1);
                System.out.println("[IT] " + type.label + " 搜索: success=" + result.isSuccess()
                        + ", count=" + result.getCompanies().size() + ", error=" + result.getError());
                // 有额度的账号应命中结果；额度受限时返回明确错误（不视为测试失败）
                if (result.isSuccess()) {
                    assertFalse(result.getCompanies().isEmpty(), type.label + " 应命中结果");
                }
            }

            // 5. 企业详情查询
            RiskbirdCompany detail = api.queryCompany("北京石头世纪科技股份有限公司");
            assertNotNull(detail);
            assertNotNull(detail.getDetailUrl());
            assertTrue(detail.getDetailUrl().startsWith("https://www.riskbird.com/ent/"));
            System.out.println("[IT] 企业详情: " + detail);
        } finally {
            browser.stopAccount(900001L);
        }
    }
}
