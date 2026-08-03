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

/**
 * 业务能力综合探测：
 * 1) 商标「汇知保」真实结果结构（校准 TRADEMARK 通道）
 * 2) 省份/地市筛选检索的 URL/接口结构
 * 3) 软著（软件著作权）查询入口
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdBizProbeTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdBizProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990009L;
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
    void probeBizCapabilities() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[BIZ] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[BIZ] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        // ============ 1. 商标「汇知保」 ============
        System.out.println("\n========== 1. 商标查询: 汇知保 ==========");
        probeUrl("商标-汇知保", "https://www.riskbird.com/search/trademark?keyword=" + enc("汇知保") + "&timestamp=" + now());

        // ============ 2. 省份/地市筛选检索 ============
        System.out.println("\n========== 2. 省份/地市筛选检索 ==========");
        // 候选 URL 参数：province / area / region
        probeUrl("查公司+省份参数", "https://www.riskbird.com/search/company?keyword=" + enc("科技")
                + "&province=" + enc("浙江") + "&timestamp=" + now());
        probeUrl("查公司+省份ID参数", "https://www.riskbird.com/search/company?keyword=" + enc("科技")
                + "&provinceId=330000&timestamp=" + now());

        // ============ 3. 软著查询入口 ============
        System.out.println("\n========== 3. 软著查询入口探测 ==========");
        probeUrl("软著-候选software", "https://www.riskbird.com/search/software?keyword=" + enc("汇知保") + "&timestamp=" + now());
        probeUrl("软著-候选softcopyright", "https://www.riskbird.com/search/softcopyright?keyword=" + enc("汇知保") + "&timestamp=" + now());
        probeUrl("软著-候选copyright", "https://www.riskbird.com/search/copyright?keyword=" + enc("汇知保") + "&timestamp=" + now());

        // ============ 4. 企业详情页商标/软著 tab 与接口 ============
        System.out.println("\n========== 4. 详情页商标/软著 tab 与接口 ==========");
        probeDetailTabs();
    }

    /** 进真实企业详情页（阿里巴巴），dump 商标/软著 tab 与相关接口。 */
    private void probeDetailTabs() throws Exception {
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/ent/" + enc("阿里巴巴（中国）有限公司")
                    + ".html?entid=v3r2xik2nNx";
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
                System.out.println("[BIZ] 详情页 Title: " + page.title());
                String body = page.evalString("(document.body.innerText || '').slice(0, 2500)");
                System.out.println("[BIZ] 详情文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(1500, body.length()))));

                // 商标/软著相关 tab/链接
                var tabs = page.evaluate("Array.from(document.querySelectorAll('a, div, span, li')).filter(el => {"
                        + " const t = (el.innerText || '').trim();"
                        + " return t === '商标' || t === '软著' || t === '软件著作权' || t === '专利' || t === '著作权';"
                        + "}).map(el => ({tag: el.tagName, text: (el.innerText||'').trim(), "
                        + "cls: (el.className && el.className.toString) ? el.className.toString().slice(0,60) : ''})).slice(0, 10)");
                System.out.println("[BIZ] 商标/软著 tab: " + tabs);
            }
            // 商标/软著相关接口
            System.out.println("[BIZ] 商标/软著接口:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("trademark") || u.contains("soft") || u.contains("copyright")
                        || u.contains("patent") || u.contains("zhuanli") || u.contains("ruanzhu")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[BIZ]   API " + r.method + " " + (u.length() > 130 ? u.substring(0, 130) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                    if (resp != null && resp.length() < 300) {
                        System.out.println("[BIZ]     body: " + resp.replace('\n', ' '));
                    }
                    if (++shown >= 6) break;
                }
            }
        }
    }

    private void probeUrl(String label, String url) throws Exception {
        System.out.println("\n---- " + label + " ----");
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);
                System.out.println("[BIZ] URL: " + page.url());
                System.out.println("[BIZ] Title: " + page.title());
                String body = page.evalString("(document.body.innerText || '').slice(0, 1200)");
                System.out.println("[BIZ] 文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(700, body.length()))));
                String totalText = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/为您找到\\s*([\\d,]+)\\s*条|共\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[BIZ] 总数: " + totalText);
            }
            // 相关接口
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("riskbird-api") || u.contains("/search") || u.contains("trademark")
                        || u.contains("software") || u.contains("copyright") || u.contains("province")
                        || u.contains("region")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[BIZ]   API " + r.method + " " + (u.length() > 130 ? u.substring(0, 130) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                    if (resp != null && resp.length() < 250) {
                        System.out.println("[BIZ]     body: " + resp.replace('\n', ' '));
                    }
                    if (++shown >= 5) break;
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
