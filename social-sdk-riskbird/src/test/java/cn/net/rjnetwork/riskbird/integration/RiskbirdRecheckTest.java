package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdIntellectualProperty;
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
 * 额度恢复后的真实联调复验：
 * 1) 带省份/地市筛选的企业检索（search + RiskbirdSearchFilter）
 * 2) 企业知识产权（商标/软著/专利）解析（queryIntellectualProperty）
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdRecheckTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdRecheckTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990012L;
    private static final String RB_COOKIE = System.getProperty("rb.cookie", "");

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(false);
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
    void recheckFilterSearchAndIp() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[RECHECK] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[RECHECK] Cookie 登录: success=" + login.isSuccess());
        assertTrue(login.isSuccess(), "登录失败: " + login.getMessage());
        assertTrue(api.isLoggedIn(), "isLoggedIn 应识别登录态");

        // ============ 1. 带省份筛选的企业检索 ============
        System.out.println("\n========== 1. 带省份筛选的企业检索 ==========");
        RiskbirdSearchResult filtered = api.search(RiskbirdConfig.QueryType.COMPANY, "软件", 1,
                RiskbirdSearchFilter.builder().province("浙江").industry("软件和信息技术服务业").build());
        System.out.println("[RECHECK] 筛选检索: success=" + filtered.isSuccess()
                + ", count=" + filtered.getCompanies().size() + ", total=" + filtered.getTotal()
                + ", error=" + filtered.getError() + ", channel=" + filtered.getChannel());
        if (filtered.isSuccess() && !filtered.getCompanies().isEmpty()) {
            for (int i = 0; i < Math.min(3, filtered.getCompanies().size()); i++) {
                System.out.println("[RECHECK]   第" + (i + 1) + "条: " + filtered.getCompanies().get(i));
            }
        }

        // ============ 2. 企业详情电话（业务环节②）============
        System.out.println("\n========== 2. 企业详情电话 ==========");
        String entId = null;
        if (filtered.isSuccess() && !filtered.getCompanies().isEmpty()) {
            RiskbirdCompany c0 = filtered.getCompanies().get(0);
            RiskbirdCompany detail = api.queryCompany(c0.getName(), c0.getEntId());
            System.out.println("[RECHECK] 详情: " + detail.getName()
                    + " | phone=" + detail.getPhone() + " | email=" + detail.getEmail()
                    + " | 法人=" + detail.getLegalPerson() + " | 状态=" + detail.getStatus());
            entId = detail.getEntId() != null ? detail.getEntId() : c0.getEntId();
        }

        // ============ 3. 企业知识产权（商标/软著/专利）============
        System.out.println("\n========== 3. 企业知识产权 ==========");
        String companyName = "阿里巴巴（中国）有限公司";
        String ipEntId = "v3r2xik2nNx"; // 实测已知 entId
        RiskbirdIntellectualProperty ip = api.queryIntellectualProperty(companyName, ipEntId);
        System.out.println("[RECHECK] 商标(" + ip.getTrademarks().size() + "): " + ip.getTrademarks());
        System.out.println("[RECHECK] 软著(" + ip.getSoftCopyrights().size() + "): " + ip.getSoftCopyrights());
        System.out.println("[RECHECK] 专利(" + ip.getPatents().size() + "): " + ip.getPatents());
        System.out.println("[RECHECK] rawText 前 300: "
                + (ip.getRawText() == null ? "null" : ip.getRawText().substring(0, Math.min(300, ip.getRawText().length()))));
    }
}
