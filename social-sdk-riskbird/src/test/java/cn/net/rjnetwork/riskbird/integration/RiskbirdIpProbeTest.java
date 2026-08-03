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
 * 软著（软件著作权）/商标查询入口聚焦探测：
 * 详情页点击「知识产权」tab（文本含数字后缀，用 contains 匹配），dump 商标/软著/专利接口。
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdIpProbeTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdIpProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990011L;
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
    void probeIpTabs() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[IP] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[IP] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        // ============ 1. 详情页 dump 全部 tab ============
        System.out.println("\n========== 1. 详情页 tab 列表 ==========");
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/ent/" + enc("阿里巴巴（中国）有限公司")
                    + ".html?entid=v3r2xik2nNx";
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(5000);
                // dump 所有 tab（含数字后缀）
                var tabs = page.evaluate("Array.from(document.querySelectorAll('a,div,span,li')).filter(el => {"
                        + " const t = (el.innerText || '').trim();"
                        + " return /^(商标|软著|软件著作权|专利|著作权|知识产权|基本|风险|经营|发展)/.test(t) && el.children.length === 0;"
                        + "}).map(el => (el.innerText||'').trim().slice(0, 30)).slice(0, 20)");
                System.out.println("[IP] tabs: " + tabs);

                // 点击「知识产权」tab（contains 匹配）
                boolean clicked = page.evalBool("(() => { "
                        + "const els = Array.from(document.querySelectorAll('a,div,span,li')).filter(e => "
                        + "  (e.innerText||'').includes('知识产权') && e.children.length === 0); "
                        + "if (els.length > 0) { els[0].click(); return true; } return false; })()");
                System.out.println("[IP] 点击知识产权: " + clicked);
                Thread.sleep(4000);

                // 知识产权区文本（含商标/软著/专利入口）
                String body = page.evalString("(() => { "
                        + "const t = document.body.innerText || ''; "
                        + "const i = t.indexOf('知识产权'); "
                        + "return i >= 0 ? t.substring(i, Math.min(t.length, i + 500)).replace(/\\s+/g, '|') : '(未找到)'; })()");
                System.out.println("[IP] 知识产权区文本: " + body);
            }
            // 商标/软著/专利接口
            System.out.println("[IP] 知识产权接口:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("trademark") || u.contains("soft") || u.contains("copyright")
                        || u.contains("patent") || u.contains("zhuanli") || u.contains("ip/")
                        || u.contains("intellect") || u.contains("zhishi") || u.contains("chuangxin")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[IP]   " + r.method + " " + (u.length() > 120 ? u.substring(0, 120) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars"));
                    if (resp != null && resp.length() < 300) {
                        System.out.println("[IP]     body: " + resp.replace('\n', ' '));
                    }
                    if (++shown >= 8) break;
                }
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
