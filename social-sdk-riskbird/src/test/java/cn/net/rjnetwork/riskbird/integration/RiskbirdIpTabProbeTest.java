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
 * 详情页「知识产权」tab 聚焦探测：
 * dump tab 真实 DOM（可点击元素/class），点击后 dump 商标/软著/专利区内容与接口。
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdIpTabProbeTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdIpTabProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990013L;
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
    void probeIpTab() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[IPTAB] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[IPTAB] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/ent/" + enc("阿里巴巴（中国）有限公司")
                    + ".html?entid=v3r2xik2nNx";
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(6000);

                // 1. dump 所有含「知识产权」的元素（找可点击 tab）
                System.out.println("\n========== 1. 「知识产权」相关元素 ==========");
                var els = page.evaluate("Array.from(document.querySelectorAll('a,div,span,li,p')).filter(e => "
                        + "  (e.innerText||'').includes('知识产权'))"
                        + "  .map(e => ({tag: e.tagName, text: (e.innerText||'').trim().slice(0,40), "
                        + "  cls: (e.className && e.className.toString) ? e.className.toString().slice(0,80) : '', "
                        + "  children: e.children.length, visible: e.getBoundingClientRect().width > 0}))"
                        + "  .slice(0, 15)");
                System.out.println("[IPTAB] 知识产权元素: " + els);

                // 2. 尝试点击可见的「知识产权」tab（有文本且可见，优先叶子）
                System.out.println("\n========== 2. 点击知识产权 tab ==========");
                boolean clicked = page.evalBool("(() => { "
                        + "const els = Array.from(document.querySelectorAll('a,div,span,li')).filter(e => "
                        + "  (e.innerText||'').trim().startsWith('知识产权') && e.getBoundingClientRect().width > 0); "
                        + "const target = els[els.length - 1]; "
                        + "if (target) { target.click(); return true; } return false; })()");
                System.out.println("[IPTAB] 点击: " + clicked);
                Thread.sleep(4000);

                // 3. 点击后 dump 商标/软著区内容
                System.out.println("\n========== 3. 点击后内容 ==========");
                String body = page.evalString("(document.body.innerText || '').slice(0, 3000)");
                System.out.println("[IPTAB] 文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(1800, body.length()))));

                // 商标/软著关键字定位
                String kw = page.evalString("(() => { "
                        + "const t = document.body.innerText || ''; "
                        + "const grab = (k) => { const i = t.indexOf(k); return i >= 0 ? t.substring(i, Math.min(t.length, i + 200)).replace(/\\s+/g,'|') : null; }; "
                        + "return JSON.stringify({商标: grab('商标'), 软著: grab('软著'), 专利: grab('专利')}); })()");
                System.out.println("[IPTAB] 关键字: " + kw);
            }
            // 4. 商标/软著接口
            System.out.println("\n========== 4. 知识产权接口 ==========");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("trademark") || u.contains("soft") || u.contains("copyright")
                        || u.contains("patent") || u.contains("zhuanli") || u.contains("ip")
                        || u.contains("intellect") || u.contains("brand") || u.contains("zhishi")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[IPTAB]   " + r.method + " " + (u.length() > 120 ? u.substring(0, 120) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars")
                            + (r.postData != null ? " | post=" + r.postData.substring(0, Math.min(150, r.postData.length())) : ""));
                    if (++shown >= 8) break;
                }
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
