package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 新能力探测：商标查询 / 公司名模糊查询 / 人员电话查找的真实 URL 与结果结构。
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdCapabilityProbeTest -DfailIfNoTests=false -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie 提供已登录 Cookie，默认跳过")
class RiskbirdCapabilityProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990008L;
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
    void probeCapabilities() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[CAP] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[CAP] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        // 候选能力 URL 探测
        probeUrl("商标查询-候选1", "https://www.riskbird.com/search/trademark?keyword=" + enc("阿里") + "&timestamp=" + now());
        probeUrl("商标查询-候选2", "https://www.riskbird.com/trademark?keyword=" + enc("阿里") + "&timestamp=" + now());
        probeUrl("人员查询", "https://www.riskbird.com/search/person?keyword=" + enc("马云") + "&timestamp=" + now());
        probeUrl("老板查询(对照)", "https://www.riskbird.com/search/boss?keyword=" + enc("马云") + "&timestamp=" + now());
        probeUrl("模糊查询-短词", "https://www.riskbird.com/search/company?keyword=" + enc("阿里") + "&timestamp=" + now());
        probeUrl("模糊查询-人名", "https://www.riskbird.com/search/company?keyword=" + enc("蒋芳") + "&timestamp=" + now());
    }

    private void probeUrl(String label, String url) throws Exception {
        System.out.println("\n========== " + label + " ==========");
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
                System.out.println("[CAP] URL: " + page.url());
                System.out.println("[CAP] Title: " + page.title());
                String body = page.evalString("(document.body.innerText || '').slice(0, 1200)");
                System.out.println("[CAP] 文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(700, body.length()))));
                String totalText = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/为您找到\\s*([\\d,]+)\\s*条|共\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[CAP] 总数: " + totalText);
            }
            // 接口
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("riskbird-api") || u.contains("/search") || u.contains("trademark")
                        || u.contains("phone") || u.contains("contact") || u.contains("person")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[CAP]   API " + r.method + " " + (u.length() > 120 ? u.substring(0, 120) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                    if (resp != null && resp.length() < 300) {
                        System.out.println("[CAP]     body: " + resp.replace('\n', ' '));
                    }
                }
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
