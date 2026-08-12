package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchFilter;
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
 * 带筛选的 search(QueryType, keyword, page, filter) 真实联调（本地 Chrome + 真实站点）。
 *
 * <p>验证 SDK 完整链路：cookie 登录 → 导航搜索页 → 等 SPA hydration → 等 filter-item-tag-item →
 * applyFilter（省/市/行业/状态）→ 等总数 → 解析结果列表。
 *
 * <p>运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdFilterSearchE2eTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdFilterSearchE2eTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990020L;
    private static final String RB_COOKIE = System.getProperty("rb.cookie", "");

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(true); // 复刻线上服务器环境（headless Chrome）
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
    void searchWithRegionFilter() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[FLT] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);

        // 1. Cookie 登录
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[FLT] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            System.out.println("[FLT] 登录失败，跳过筛选测试");
            return;
        }

        // 2. 带筛选搜索：省份=河南省、地市=周口市、状态=在营（复刻真实采集 filter_json）
        RiskbirdSearchFilter filter = RiskbirdSearchFilter.builder()
                .province("河南省")
                .city("周口市")
                .status("在营")
                .build();
        System.out.println("[FLT] 开始带筛选搜索: province=" + filter.getProvince()
                + ", city=" + filter.getCity() + ", status=" + filter.getStatus());

        RiskbirdSearchResult result = api.search(RiskbirdConfig.QueryType.COMPANY, "软件", 1, filter);
        System.out.println("[FLT] 搜索结果: success=" + result.isSuccess()
                + ", total=" + result.getTotal()
                + ", companies=" + (result.getCompanies() == null ? 0 : result.getCompanies().size())
                + ", error=" + result.getError()
                + ", channel=" + result.getChannel());

        if (result.isSuccess() && result.getCompanies() != null && !result.getCompanies().isEmpty()) {
            System.out.println("[FLT] 首条结果: " + result.getCompanies().get(0).getName());
        }
        // 断言：带筛选搜索应成功并采到结果（total > 0 或 companies 非空）
        assertTrue(result.isSuccess(), "带筛选搜索应成功: " + result.getError());
        assertNotNull(result.getCompanies());
        assertFalse(result.getCompanies().isEmpty(),
                "带筛选搜索应采到结果（total=" + result.getTotal() + "）");
        System.out.println("[FLT] *** SDK search 完整筛选链路测通！采到 " + result.getCompanies().size() + " 条 ***");
    }
}
