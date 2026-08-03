package cn.net.rjnetwork.riskbird.integration;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeHealthChecker;
import cn.net.rjnetwork.xianyu.chrome.core.ChromePortPool;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeProfileManager;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeSession;
import cn.net.rjnetwork.xianyu.chrome.page.ChromePage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * riskbird 站点真实结构探索（真实联调用，非断言测试）。
 *
 * <p>启动本机 Chrome 访问 riskbird.com，dump 出登录入口 / 搜索框 / 登录弹窗表单 / 搜索结果
 * 的真实选择器与网络请求 URL，用于校准 social-sdk-riskbird 的封装配置。
 *
 * <p>运行：mvn -pl social-sdk-riskbird test -Dtest=RiskbirdSiteProbeTest -DfailIfNoTests=false
 */
@Disabled("真实联调探索工具：需本地 Chrome，默认跳过")
class RiskbirdSiteProbeTest {

    private static ChromeBrowser browser;
    private static final long PROBE_ACCOUNT = 990001L;

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
        // 使用本机已安装的 Chrome
        config.setExecutablePath("C:/Program Files/Google/Chrome/Application/chrome.exe");
        config.setHeadless(false);
        config.setMaxActiveProfiles(2);
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
            browser.stopAccount(PROBE_ACCOUNT);
        }
    }

    @Test
    void probeSite() throws Exception {
        try (ChromePage page = browser.openPage(PROBE_ACCOUNT)) {
            // ---- 1. 首页结构 ----
            page.navigate("https://www.riskbird.com/");
            Thread.sleep(3000);
            System.out.println("\n===== 1. 首页 =====");
            System.out.println("URL: " + page.url());
            System.out.println("Title: " + page.title());

            // ---- 2. 搜索框 ----
            System.out.println("\n===== 2. 搜索框（input 元素）=====");
            dumpInputs(page);

            // ---- 3. 登录入口 ----
            System.out.println("\n===== 3. 登录入口（含'登录'文本的元素）=====");
            dumpElements(page, "a, button, span, div", "登录", 10);

            // ---- 3.5 五类查询导航 href（查公司/查老板/查风险/查文书/查关系）----
            System.out.println("\n===== 3.5 五类查询导航 href =====");
            var nav = page.evaluate("Array.from(document.querySelectorAll('a')).filter(a => {"
                    + " const t = (a.innerText || '').trim();"
                    + " return t === '查公司' || t === '查老板' || t === '查风险' || t === '查文书' || t === '查关系';"
                    + "}).map(a => ({text: (a.innerText || '').trim(), href: a.href || ''}))");
            if (nav != null && nav.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : nav) {
                    System.out.println("  " + n.toString());
                }
            }

            // ---- 4. 打开登录弹窗，dump 内部结构 ----
            System.out.println("\n===== 4. 登录弹窗结构 =====");
            probeLoginDialog(page);
        }
    }

    /** 点击「登录/注册」打开扫码弹窗，dump 弹窗内二维码/iframe/表单结构。 */
    private void probeLoginDialog(ChromePage page) throws Exception {
        // 点击登录按钮（真实 class: userinfo-auth-btn-gohst）
        boolean clicked = false;
        for (String sel : new String[]{
                "[class*=userinfo-auth-btn]", "[class*=userinfo-auth]", "div.userinfo-wrap a, div.userinfo-wrap div"}) {
            if (page.exists(sel)) {
                System.out.println("  点击登录入口: " + sel);
                page.click(sel);
                clicked = true;
                break;
            }
        }
        if (!clicked) {
            System.out.println("  未找到登录入口可点击元素");
            return;
        }
        Thread.sleep(3000);

        // 弹窗完整文本（确认登录方式入口）
        System.out.println("\n  --- 弹窗 innerText ---");
        String dlgText = page.evalString("(() => { "
                + "const dlg = document.querySelector('.popover-login-img')?.closest('[class*=popover]') || document.body; "
                + "return (dlg.innerText || '').slice(0, 800); })()");
        System.out.println(dlgText == null ? "(null)" : dlgText);

        // 弹窗内所有 a / button（登录方式入口）
        System.out.println("\n  --- 弹窗内可点击入口（a/button）---");
        var links = page.evaluate("Array.from(document.querySelectorAll('.popover-login-img, [class*=popover] a, [class*=popover] button, [class*=login] a, [class*=login] button'))"
                + ".map(el => ({tag: el.tagName, text: (el.innerText || '').trim().slice(0, 40), "
                + "href: el.href || '', cls: (el.className && el.className.toString) ? el.className.toString().slice(0, 60) : ''}))");
        if (links != null && links.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : links) {
                System.out.println("  " + n.toString());
            }
        }

        // 二维码候选：src 含 qr / code / scan 的 img，以及 canvas
        System.out.println("\n  --- 二维码候选元素（点击'登录试试'前）---");
        dumpQrCandidates(page);

        // ---- 5. 点击「登录试试」打开真正的扫码登录 ----
        System.out.println("\n  --- 点击'登录试试' ---");
        boolean clickedTry = false;
        for (String sel : new String[]{".popover-btn", "button.xs-button", "button"}) {
            if (page.exists(sel)) {
                System.out.println("  点击: " + sel);
                page.click(sel);
                clickedTry = true;
                break;
            }
        }
        Thread.sleep(4000);
        System.out.println("  点击后 URL: " + page.url());

        // 检查是否新开标签页（登录页常新开 target）
        System.out.println("\n  --- 点击后所有标签页 ---");
        var tabs = browser.listTabs(PROBE_ACCOUNT);
        for (var tab : tabs) {
            System.out.println("  " + tab);
        }

        // 如果有新标签页（登录页），连上去 dump 结构
        for (var tab : tabs) {
            if (!tab.url.startsWith("https://www.riskbird.com/")) {
                System.out.println("\n  --- 新标签页（登录页）结构: " + tab.url + " ---");
                try (cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession cdp = browser.connectToTab(PROBE_ACCOUNT, tab.targetId)) {
                    Thread.sleep(3000);
                    String bodyText = page.evalString("(document.body.innerText || '').slice(0, 600)");
                    System.out.println("  文本: " + bodyText);
                    var frames2 = page.evaluate("Array.from(document.querySelectorAll('iframe')).map(i => ({"
                            + "src: (i.src || '').slice(0, 200), cls: (i.className && i.className.toString) ? i.className.toString() : ''}))");
                    if (frames2 != null && frames2.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode n : frames2) {
                            System.out.println("  iframe: " + n.toString());
                        }
                    }
                }
            }
        }

        System.out.println("\n  --- iframe 完整列表（当前页）---");
        var frames = page.evaluate("Array.from(document.querySelectorAll('iframe')).map(i => ({"
                + "src: (i.src || '').slice(0, 200), cls: (i.className && i.className.toString) ? i.className.toString() : '', "
                + "id: i.id || ''}))");
        if (frames != null && frames.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : frames) {
                System.out.println("  " + n.toString());
            }
        }

        System.out.println("\n  --- 二维码候选元素（点击'登录试试'后）---");
        dumpQrCandidates(page);

        System.out.println("\n  --- 点击后弹窗/登录区文本 ---");
        String postText = page.evalString("(() => { "
                + "const dlg = document.querySelector('[class*=dialog] [class*=content], [class*=login] [class*=content], [class*=qr], [class*=code]') || document.body; "
                + "return (dlg.innerText || '').slice(0, 600); })()");
        System.out.println(postText == null ? "(null)" : postText);
    }

    /** dump 二维码候选 img 与 canvas 数量。 */
    private void dumpQrCandidates(ChromePage page) throws Exception {
        var qrCandidates = page.evaluate("(() => { "
                + "const imgs = Array.from(document.querySelectorAll('img')).filter(i => "
                + "  /qr|code|scan|weixin|wechat|login/i.test((i.src || '') + ' ' + (i.className || '')))"
                + "  .map(i => ({src: (i.src || '').slice(0, 150), cls: i.className ? i.className.toString() : ''})); "
                + "const canvases = Array.from(document.querySelectorAll('canvas')).length; "
                + "return {imgs, canvases}; })()");
        if (qrCandidates != null) {
            System.out.println("  canvas 数量: " + qrCandidates.path("canvases").asInt());
            for (com.fasterxml.jackson.databind.JsonNode n : qrCandidates.path("imgs")) {
                System.out.println("  " + n.toString());
            }
        }
    }

    /** 列出页面所有 input 的 name/id/placeholder/class。 */
    private void dumpInputs(ChromePage page) throws Exception {
        var v = page.evaluate("Array.from(document.querySelectorAll('input')).map(i => ({"
                + "name: i.name || '', id: i.id || '', type: i.type || '', ph: i.placeholder || '', "
                + "cls: (i.className && i.className.toString) ? i.className.toString() : ''"
                + "}))");
        if (v != null && v.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : v) {
                System.out.println("  input " + n.toString());
            }
        } else {
            System.out.println("  (无 input 元素或已折叠)");
        }
    }

    /** 输出文本包含关键字的元素及其标签/class。 */
    private void dumpElements(ChromePage page, String selector, String keyword, int max) throws Exception {
        var v = page.evaluate("Array.from(document.querySelectorAll(" + esc(selector) + "))"
                + ".filter(el => el.innerText && el.innerText.includes(" + esc(keyword) + "))"
                + ".slice(0, " + max + ").map(el => ({"
                + "tag: el.tagName, text: (el.innerText || '').trim().slice(0, 40), "
                + "cls: (el.className && el.className.toString) ? el.className.toString().slice(0, 80) : '', "
                + "href: el.href || ''"
                + "}))");
        if (v != null && v.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : v) {
                System.out.println("  " + n.toString());
            }
            if (v.isEmpty()) {
                System.out.println("  (未找到含 '" + keyword + "' 的元素)");
            }
        } else {
            System.out.println("  (查询失败)");
        }
    }

    /** 搜索尝试：填搜索框 → 回车 → 观察 URL / 新元素 / 请求。 */
    private void trySearch(ChromePage page) throws Exception {
        boolean typed = false;
        // 尝试常见搜索框选择器
        for (String sel : new String[]{"input[type=text]", "input[placeholder*='搜索']", "input[placeholder*='公司']"}) {
            if (page.exists(sel)) {
                System.out.println("  使用搜索框: " + sel);
                page.click(sel);
                page.insertText("阿里巴巴");
                Thread.sleep(800);
                page.pressKey("Enter");
                typed = true;
                break;
            }
        }
        Thread.sleep(4000);
        System.out.println("  搜索后 URL: " + page.url());
        System.out.println("  搜索后 Title: " + page.title());

        // 搜索结果列表
        System.out.println("\n===== 5. 搜索结果候选元素 =====");
        dumpElements(page, "[class*='list'] li, [class*='result'] a, [class*='ent'] a, [class*='company'] a, [class*='item'] a", "", 8);

        // 输出页面部分文本（前 800 字符）帮助判断
        String bodyText = page.evalString("(document.body.innerText || '').slice(0, 800)");
        System.out.println("\n===== 6. 页面文本前 800 字符 =====");
        System.out.println(bodyText);
    }

    private static String esc(String s) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(s == null ? "" : s);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
