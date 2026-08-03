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
 * 省份/地市筛选 + 详情页商标/软著接口探测（真实 Cookie 联调）。
 *
 * 运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdRegionProbeTest -DfailIfNoTests=false
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' -Drb.cookie="..."
 */
@Disabled("真实联调：需 -Drb.cookie，默认跳过")
class RiskbirdRegionProbeTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990010L;
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
    void probeRegionFilter() throws Exception {
        if (RB_COOKIE.isBlank()) {
            System.out.println("[REG] 未提供 -Drb.cookie，跳过");
            return;
        }
        RiskbirdConfig config = new RiskbirdConfig();
        ChromeRiskbirdDriver driver = new ChromeRiskbirdDriver(config, browser, ACCOUNT);
        RiskbirdApiFacade api = new RiskbirdApiFacade(config, driver);
        RiskbirdLoginResult login = api.loginWithCookie(RB_COOKIE);
        System.out.println("[REG] Cookie 登录: success=" + login.isSuccess());
        if (!login.isSuccess()) {
            return;
        }

        // ============ 1. 搜索页省份/地市筛选结构 ============
        System.out.println("\n========== 1. 搜索页筛选结构 ==========");
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                String url = "https://www.riskbird.com/search/company?keyword="
                        + enc("科技") + "&timestamp=" + System.currentTimeMillis();
                page.navigate(url);
                Thread.sleep(6000);
                System.out.println("[REG] URL: " + page.url());

                // dump 省份/地市/行业筛选区结构
                var filters = page.evaluate("(() => { "
                        + "const out = {}; "
                        + "['省份地区', '地市', '城市', '行业', '企业状态'].forEach(kw => { "
                        + "  const el = Array.from(document.querySelectorAll('div,span,li,a')).find(e => "
                        + "    (e.innerText||'').trim() === kw); "
                        + "  if (el) { const parent = el.parentElement; "
                        + "    out[kw] = {cls: (parent && parent.className) ? parent.className.toString().slice(0,80) : ''}; } "
                        + "}); "
                        + "return out; })()");
                System.out.println("[REG] 筛选区: " + filters);

                // 找「省份地区」下可点击的省份（浙江）
                var provinceEls = page.evaluate("(() => { "
                        + "const t = document.body.innerText || ''; "
                        + "const i = t.indexOf('省份地区'); "
                        + "return i >= 0 ? t.substring(i, i + 200).replace(/\\s+/g, ' ').trim() : '(未找到)'; })()");
                System.out.println("[REG] 省份地区区块文本: " + provinceEls);
            }

            // ============ 2. 点击「浙江」省份筛选 → dump 接口请求体 ============
            System.out.println("\n========== 2. 点击省份筛选后的接口 ==========");
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                String url = "https://www.riskbird.com/search/company?keyword="
                        + enc("科技") + "&timestamp=" + System.currentTimeMillis();
                page.navigate(url);
                Thread.sleep(5000);
                // 点击文本为「浙江」的元素（筛选项）
                boolean clicked = page.evalBool("(() => { "
                        + "const els = Array.from(document.querySelectorAll('span,div,li,a')).filter(e => "
                        + "  (e.innerText||'').trim() === '浙江' && e.children.length === 0); "
                        + "if (els.length > 0) { els[0].click(); return true; } return false; })()");
                System.out.println("[REG] 点击浙江: " + clicked);
                Thread.sleep(4000);
                System.out.println("[REG] 点击后 URL: " + page.url());
                String total = page.evalString("(() => { "
                        + "const m = (document.body.innerText || '').match(/为您找到\\s*([\\d,]+)\\s*条/); "
                        + "return m ? m[0] : null; })()");
                System.out.println("[REG] 筛选后总数: " + total);
            }
            // dump 搜索接口（POST 请求体）
            System.out.println("[REG] 搜索接口请求:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("riskbird-api") || u.contains("companys") || u.contains("search")) {
                    System.out.println("[REG]   " + r.method + " " + (u.length() > 140 ? u.substring(0, 140) : u)
                            + " | type=" + r.resourceType
                            + " | post=" + (r.postData == null ? "-" : r.postData.substring(0, Math.min(200, r.postData.length()))));
                    if (++shown >= 8) break;
                }
            }
        }

        // ============ 3. 详情页「知识产权」tab → 商标/软著接口 ============
        System.out.println("\n========== 3. 详情页知识产权 tab ==========");
        probeIntellectualProperty();
    }

    /** 详情页点击「知识产权」tab，dump 商标/软著接口。 */
    private void probeIntellectualProperty() throws Exception {
        try (CdpSession cdp = browser.connectToAccount(ACCOUNT)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true);
            String url = "https://www.riskbird.com/ent/" + enc("阿里巴巴（中国）有限公司")
                    + ".html?entid=v3r2xik2nNx";
            try (ChromePage page = browser.openPage(ACCOUNT)) {
                page.navigate(url);
                Thread.sleep(5000);
                boolean clicked = page.evalBool("(() => { "
                        + "const els = Array.from(document.querySelectorAll('a,div,span,li')).filter(e => "
                        + "  (e.innerText||'').trim() === '知识产权' && e.children.length === 0); "
                        + "if (els.length > 0) { els[0].click(); return true; } return false; })()");
                System.out.println("[REG] 点击知识产权 tab: " + clicked);
                Thread.sleep(4000);
                String body = page.evalString("(document.body.innerText || '').slice(0, 1200)");
                System.out.println("[REG] 知识产权区文本: " + (body == null ? "null"
                        : body.replace('\n', '|').substring(0, Math.min(700, body.length()))));
            }
            System.out.println("[REG] 知识产权接口:");
            int shown = 0;
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String u = r.url == null ? "" : r.url;
                if (u.contains("trademark") || u.contains("soft") || u.contains("copyright")
                        || u.contains("patent") || u.contains("zhuanli") || u.contains("ip") || u.contains("intellect")) {
                    String resp = net.body(r.requestId);
                    System.out.println("[REG]   API " + r.method + " " + (u.length() > 130 ? u.substring(0, 130) : u)
                            + " → " + (resp == null ? "(无)" : resp.length() + " chars")
                            + (r.postData != null ? " | post=" + r.postData.substring(0, Math.min(150, r.postData.length())) : ""));
                    if (++shown >= 6) break;
                }
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
