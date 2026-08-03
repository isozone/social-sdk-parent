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
 * 登录 popover 触发问题诊断（真实联调）。
 * 逐步复现 prepareQrLogin 并 dump 每步的 DOM 状态，定位 .popover-btn 不可见的原因。
 */
@Disabled("真实联调诊断工具：需本地 Chrome，默认跳过")
class RiskbirdLoginDebugTest {

    private static ChromeBrowser browser;
    private static final long ACCOUNT = 990003L;

    @BeforeAll
    static void setup() {
        ChromeConfig config = new ChromeConfig();
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
            browser.stopAccount(ACCOUNT);
        }
    }

    @Test
    void debugPopover() throws Exception {
        try (ChromePage page = browser.openPage(ACCOUNT)) {
            page.navigate("https://www.riskbird.com/");
            Thread.sleep(3000);
            System.out.println("\n[DBG] URL=" + page.url());

            // 登录入口状态
            System.out.println("[DBG] entry exists=" + page.exists("[class*=userinfo-auth-btn]")
                    + ", visible=" + page.isVisible("[class*=userinfo-auth-btn]"));
            var entryInfo = page.evaluate("(() => { const el = document.querySelector('[class*=userinfo-auth-btn]'); "
                    + "if (!el) return null; const r = el.getBoundingClientRect(); return {"
                    + "tag: el.tagName, text: (el.innerText||'').trim().slice(0,30), "
                    + "x: r.x, y: r.y, w: r.width, h: r.height, "
                    + "display: getComputedStyle(el).display, cls: (el.className||'').toString()}; })()");
            System.out.println("[DBG] entry: " + entryInfo);

            // 尝试多路触发，每步 dump .popover-btn 状态
            String[] triggers = {"click", "hover", "jsclick"};
            for (int attempt = 0; attempt < 3; attempt++) {
                String trigger = triggers[attempt % triggers.length];
                if ("click".equals(trigger)) {
                    page.click("[class*=userinfo-auth-btn]");
                } else if ("hover".equals(trigger)) {
                    page.$("[class*=userinfo-auth-btn]").hover();
                } else {
                    page.evalBool("(() => { const el = document.querySelector('[class*=userinfo-auth-btn]'); "
                            + "if (el) { el.click(); return true; } return false; })()");
                }
                Thread.sleep(2000);

                var pop = page.evaluate("(() => { const el = document.querySelector('.popover-btn'); "
                        + "if (!el) return {exists: false}; const r = el.getBoundingClientRect(); return {"
                        + "exists: true, text: (el.innerText||'').trim().slice(0,30), "
                        + "x: r.x, y: r.y, w: r.width, h: r.height, "
                        + "display: getComputedStyle(el).display, visibility: getComputedStyle(el).visibility, "
                        + "parent: el.parentElement ? (el.parentElement.className||'').toString().slice(0,80) : ''}; })()");
                System.out.println("[DBG] after " + trigger + " → popover-btn: " + pop);

                // dump 所有含 popover 的容器
                var containers = page.evaluate("Array.from(document.querySelectorAll('[class*=popover]')).map(el => {"
                        + " const r = el.getBoundingClientRect(); return {"
                        + "cls: (el.className||'').toString().slice(0,80), "
                        + "visible: r.width > 0 && getComputedStyle(el).display !== 'none', "
                        + "text: (el.innerText||'').trim().slice(0,50)}; })");
                System.out.println("[DBG] popover 容器: " + containers);
            }
        }
    }
}
