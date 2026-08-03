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

/**
 * 接口响应结构探索：dump 查公司/查老板搜索接口的真实响应体前 N 字符，
 * 确认数据是 HTML 内嵌 JSON 还是独立 JSON，用于校准 API 通道的解析路径。
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdApiStructureProbeTest -DfailIfNoTests=false -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie 提供已登录 Cookie，默认跳过")
class RiskbirdApiStructureProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990007L;
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
    void dumpApiStructures() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[API] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[API] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        // 查公司响应结构
        dumpResponse("company", "阿里巴巴");
        // 查老板响应结构（关键词用人名）
        dumpResponse("boss", "马云");
        // 查风险
        dumpResponse("risk", "阿里巴巴");
    }

    private void dumpResponse(String type, String keyword) throws Exception {
        System.out.println("\n========== 响应结构: /search/" + type + "?keyword=" + keyword + " ==========");
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/search/" + type + "?keyword="
                    + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                    + "&timestamp=" + System.currentTimeMillis();
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
            }
            // 找 /search/ 接口响应
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                if (r.url != null && r.url.contains("/search/" + type + "?")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[API] URL: " + r.url);
                    if (resp == null) {
                        System.out.println("[API] (无响应体)");
                        return;
                    }
                    System.out.println("[API] 响应长度: " + resp.length());
                    // 前 600 字符
                    System.out.println("[API] 前600: " + resp.substring(0, Math.min(600, resp.length())).replace('\n', ' '));
                    // 判断是否 HTML
                    boolean isHtml = resp.trim().startsWith("<") || resp.contains("<!DOCTYPE") || resp.contains("<html");
                    System.out.println("[API] 是HTML: " + isHtml);
                    if (isHtml) {
                        // 找内嵌 JSON 特征
                        int idx = resp.indexOf("window.__");
                        System.out.println("[API] window.__ 位置: " + idx);
                        if (idx > 0) {
                            System.out.println("[API] 内嵌JSON前500: " + resp.substring(idx, Math.min(idx + 500, resp.length())).replace('\n', ' '));
                        }
                        // 找 companyName / list 等字段
                        for (String key : new String[]{"companyName", "company_name", "\"list\"", "\"data\"", "entId", "totalCount"}) {
                            int i = resp.indexOf(key);
                            if (i > 0) {
                                System.out.println("[API] 字段 " + key + " @ " + i + ": "
                                        + resp.substring(Math.max(0, i - 80), Math.min(i + 120, resp.length())).replace('\n', ' '));
                            }
                        }
                    }
                    return;
                }
            }
            System.out.println("[API] 未捕获到 /search/" + type + " 接口");
        }
    }
}
