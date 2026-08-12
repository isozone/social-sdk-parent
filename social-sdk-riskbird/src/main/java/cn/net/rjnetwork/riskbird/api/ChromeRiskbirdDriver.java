package cn.net.rjnetwork.riskbird.api;

import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdIntellectualProperty;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdPerson;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchFilter;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpCookieStore;
import cn.net.rjnetwork.xianyu.chrome.cdp.CdpSession;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import cn.net.rjnetwork.xianyu.chrome.human.HumanDelay;
import cn.net.rjnetwork.xianyu.chrome.network.ChromeNetwork;
import cn.net.rjnetwork.xianyu.chrome.page.ChromePage;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Riskbird 页面驱动默认实现：基于 social-sdk-chrome 的 {@link ChromeBrowser}，
 * 每账号独立 Chrome 容器（独立 profile / 代理 / 指纹 / 端口），天然多账户隔离。
 *
 * <p>查询走混合双通道：
 * <ol>
 *   <li><b>API 优先</b>：启用 {@link ChromeNetwork} 抓包，搜索/详情接口响应命中 URL 关键字时
 *       直接解析 JSON（稳定高效）</li>
 *   <li><b>DOM 兜底</b>：未命中接口时回落到页面 DOM 解析（选择器取自 {@link RiskbirdConfig}）</li>
 * </ol>
 */
public class ChromeRiskbirdDriver implements RiskbirdPageDriver {

    private static final Logger log = LoggerFactory.getLogger(ChromeRiskbirdDriver.class);

    private final RiskbirdConfig config;
    private final ChromeBrowser chromeBrowser;
    private final long accountId;

    /**
     * 账号最新登录态 cookie header（宿主扫码登录成功后调 setAccountCookie 注入）。
     * SDK 用它兜底：采集时 Chrome profile cookie 被 prepareQrLogin 清掉后，
     * search 开页后自动注入此 cookie 恢复登录态，否则风鸟按未登录搜命中 0 条。
     */
    private volatile String accountCookieHeader;

    /** 宿主扫码登录成功后调此注入最新 cookie，供后续 search 兜底恢复登录态。 */
    public void setAccountCookie(String cookieHeader) {
        this.accountCookieHeader = cookieHeader;
        log.info("[RISKBIRD] setAccountCookie 注入, accountId={}, cookieLen={}",
                accountId, cookieHeader == null ? 0 : cookieHeader.length());
    }

    public ChromeRiskbirdDriver(RiskbirdConfig config, ChromeBrowser chromeBrowser, long accountId) {
        this.config = config;
        this.chromeBrowser = chromeBrowser;
        this.accountId = accountId;
    }

    // ==================== 登录 ====================

    /**
     * 账号密码登录。
     *
     * <p>注意：真实站点以扫码登录为主（微信 / 风鸟App），未发现账号密码表单；
     * 本方法返回失败提示，请使用 {@link #prepareQrLogin()} + {@link #waitQrLogin(String)}。
     */
    @Override
    public RiskbirdLoginResult loginWithPassword(String username, String password)
            throws IOException, TimeoutException, InterruptedException {
        return loginResult(false, username, null, "riskbird 站点以扫码登录为主（微信/风鸟App），请使用 prepareQrLogin() + waitQrLogin()");
    }

    /**
     * 扫码登录：打开首页 → 触发「登录/注册」popover → 点击「登录试试」→ 返回二维码图片 URL。
     *
     * <p>真实链路（已实测）：{@code [class*=userinfo-auth-btn]} → {@code .popover-btn} →
     * {@code img.xs-login-left-qrcode}，其 src 形如
     * {@code https://www.riskbird.com/riskbird-api/createQrCode?uuid=xxx}。
     *
     * <p>登录入口 popover 为 hover/点击触发且 SPA 异步渲染，采用「hover → JS click 兜底 →
     * 轮询等待」的稳健交互流程。
     *
     * @return 二维码图片 URL（调用方展示后由 {@link #waitQrLogin(String)} 轮询登录态）
     */
    @Override
    public String prepareQrLogin() throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try (ChromePage page = chromeBrowser.openPage(accountId)) {
            page.navigate(config.getLoginSuccessUrl());
            // 等 SPA 渲染稳定（登录入口 / 事件绑定就绪），避免过早交互无效
            HumanDelay.sleep(2000, 3500);

            // 已登录态兜底：复用 profile 的 Chrome 可能停在登录后页（userinfo-auth-btn 不出现），
            // 导致 waitForExists 超时。检测到已登录态则清登录 cookie + reload 回未登录首页，
            // 再等登录入口。风鸟登录态靠 cookie（非 localStorage），清 cookie 即可退出。
            if (isLoggedIn(page)) {
                log.info("[RISKBIRD] 检测到已登录态, 清 cookie 后重新加载未登录页, accountId={}", accountId);
                clearRiskbirdCookies(page);
                page.navigate(config.getLoginSuccessUrl());
                HumanDelay.sleep(2000, 3500);
            }

            // 登录入口只需「存在于 DOM」即可点击，不能等「可见」：
            // 实测 Nuxt SSR hydration 早期，userinfo-auth-btn-gohst 的 offsetParent 可能短暂为 null
            // （popover 容器未挂载完成），waitForSelector→waitForVisible 会误判超时。
            waitForExists(page, config.getLoginEntrySelector(), config.getSearchTimeoutMs());

            // 触发登录 popover：真实鼠标点击登录入口（hover 亦可），让 popover 内容挂载到 DOM。
            // 实测：.popover-btn 由 Element UI popover 渲染，内容常驻 DOM 但容器隐藏（0 尺寸），
            // 故不能等「可见」，只需等「存在于 DOM」，再用 JS click 触发按钮事件。
            boolean shown = false;
            for (int attempt = 0; attempt < 3 && !shown; attempt++) {
                page.click(config.getLoginEntrySelector());
                HumanDelay.sleep(1200, 2200);
                shown = page.exists(config.getLoginTryButtonSelector());
            }
            if (!shown) {
                throw new IOException("登录 popover 未挂载, accountId=" + accountId + ", url=" + page.url());
            }

            // JS click「登录试试」（事件处理器已绑定，容器隐藏不影响）
            boolean clickedTry = page.evalBool("(() => { const el = document.querySelector("
                    + esc(config.getLoginTryButtonSelector()) + "); if (el) { el.click(); return true; } return false; })()");
            if (!clickedTry) {
                throw new IOException("未找到「登录试试」按钮, accountId=" + accountId);
            }
            HumanDelay.sleep(1200, 2200); // 等二维码异步渲染

            // 等待二维码图片出现并返回其 URL
            // 二维码 img 在 Element UI popover 内，popover 容器隐藏（0 尺寸），
            // img.offsetParent 可能为 null，故只等「存在于 DOM」而非「可见」。
            waitForExists(page, config.getQrImageSelector(), config.getSearchTimeoutMs());
            String qrUrl = page.attr(config.getQrImageSelector(), "src");
            // 风鸟页面返回的二维码 img src 是相对路径(如 /riskbird-api/createQrCode?uuid=xxx),
            // 需补全为绝对 URL,否则前端会用 CRM 域名拼接导致图片 404 无法显示
            if (qrUrl != null && qrUrl.startsWith("/")) {
                try {
                    java.net.URI pageUri = new java.net.URI(page.url());
                    String host = pageUri.getHost() == null ? "" : pageUri.getHost();
                    int port = pageUri.getPort();
                    qrUrl = pageUri.getScheme() + "://" + host + (port > 0 ? ":" + port : "") + qrUrl;
                } catch (Exception e) {
                    log.warn("[RISKBIRD] 二维码 URL 补全失败, 使用原始值: {}, err={}", qrUrl, e.getMessage());
                }
            }
            // 风鸟 createQrCode 接口需要同会话 Cookie,前端 <img> 直接加载会 401;
            // 故在 Chrome 会话内 fetch 图片并转成 base64 data URL 返回,前端可直接显示。
            String qrDataUrl = fetchQrAsDataUrl(page, qrUrl);
            if (qrDataUrl != null && !qrDataUrl.isBlank()) {
                qrUrl = qrDataUrl;
            }
            log.info("[RISKBIRD] 扫码二维码已就绪, accountId={}, qrUrl={}", accountId, qrUrl);
            return qrUrl;
        } finally {}
    }

    @Override
    public RiskbirdLoginResult waitQrLogin(String qrSession)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        long deadline = System.currentTimeMillis() + config.getLoginTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            // 登录态判定：读当前页面状态（不开新页面、不 navigate，避免打断扫码 popover）。
            // 不调无参 isLoggedIn()：Chrome 126+ 的 CDP cookie store 对 httpOnly+secure 的
            // token/userinfo cookie 完全不返回，导致永远误判未登录。
            if (isloggedInByCurrentPage()) {
                String cookie = extractCookieHeaderQuietly();
                log.info("[RISKBIRD] 扫码登录成功, accountId={}", accountId);
                return loginResult(true, null, cookie, "扫码登录成功");
            }
            Thread.sleep(config.getQrPollIntervalMs());
        }
        log.warn("[RISKBIRD] 扫码登录超时, accountId={}", accountId);
        return loginResult(false, null, null, "扫码登录超时");
    }

    /**
     * 当前页面状态判定登录态（不开新页面、不 navigate，避免打断扫码 popover）。
     * <p>
     * 扫码成功后风鸟页面会自动跳转（URL 变化）或二维码 popover 消失，
     * 此时登录入口 {@code [class*=userinfo-auth-btn]} 重新出现意味着未登录态回退，
     * 反之消失则视为已登录。判定依据（任一命中即视为已登录）：
     * <ul>
     *   <li>当前 URL 含登录成功关键字（dashboard/member/home/index 等）</li>
     *   <li>页面 title 含登录后特征（如「企业查询平台」且登录入口不存在）</li>
     *   <li>登录入口不存在于 DOM（已登录态站点会隐藏登录/注册按钮）</li>
     * </ul>
     */
    private boolean isloggedInByCurrentPage() throws IOException, TimeoutException, InterruptedException {
        try (ChromePage page = chromeBrowser.openPage(accountId)) {
            // 不 navigate：复用当前页面状态，避免销毁扫码 popover
            String url = page.url();
            if (url != null) {
                for (String kw : config.getLoginSuccessUrlKeywords()) {
                    if (url.contains(kw)) {
                        return true;
                    }
                }
            }
            // 登录入口不存在 = 已登录态（未登录态会显示登录/注册按钮）
            return !page.exists(config.getLoginEntrySelector());
        } finally {}
    }

    @Override
    public RiskbirdLoginResult loginWithCookie(String cookieHeader) throws IOException, TimeoutException {
        ensureContainer();
        try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
            // 直接把 cookie header 解析并注入（复用 CdpCookieStore）
            CdpCookieStore store = new CdpCookieStore(cdp);
            for (String pair : cookieHeader.split(";")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String name = pair.substring(0, idx).trim();
                    String value = pair.substring(idx + 1).trim();
                    if (!name.isEmpty()) {
                        store.setCookie(name, value, config.getBaseUrl());
                    }
                }
            }
            boolean loggedIn = isLoggedIn();
            String extracted = loggedIn ? extractCookieHeaderQuietly() : null;
            return loginResult(loggedIn, null, extracted,
                    loggedIn ? "Cookie 登录成功" : "Cookie 已注入，但未检测到有效登录态");
        }
    }

    /** 登录态提取为 cookie header（best-effort，失败返回 null 不中断登录流程）。 */
    private String extractCookieHeaderQuietly() {
        try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
            return new CdpCookieStore(cdp).toHeaderValue(config.getBaseUrl());
        } catch (Exception e) {
            log.debug("[RISKBIRD] 登录后提取 Cookie 失败(忽略): {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isLoggedIn() throws IOException, TimeoutException {
        ensureContainer();
        try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
            CdpCookieStore store = new CdpCookieStore(cdp);
            List<CdpCookieStore.Cookie> cookies = store.getCookies(config.getBaseUrl());

            // 1. 匿名标记：X-Canary-Reason=ANONYMOUS* → 明确未登录（优先判定）
            for (CdpCookieStore.Cookie c : cookies) {
                if ("X-Canary-Reason".equalsIgnoreCase(c.name)
                        && c.value != null && c.value.toUpperCase().contains("ANONYMOUS")) {
                    return false;
                }
            }
            // 2. 设备/灰度标识不算登录态（app-uuid 含 uid 子串，必须排除，避免误判）
            // 3. 真实登录态特征：token（JWT）/ userinfo（含 userId）/ passport / session / ticket
            for (CdpCookieStore.Cookie c : cookies) {
                String n = c.name == null ? "" : c.name.toLowerCase();
                if (n.startsWith("x-canary") || "app-uuid".equals(n) || "app-device".equals(n)
                        || "first-authorization".equals(n)) {
                    continue;
                }
                if (n.contains("token") || n.equals("userinfo")
                        || n.contains("passport") || n.contains("session") || n.contains("ticket")) {
                    return true;
                }
            }
            return false;
        } finally {}
    }

    @Override
    public String extractCookieHeader() throws IOException, TimeoutException {
        ensureContainer();
        try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
            return new CdpCookieStore(cdp).toHeaderValue(config.getBaseUrl());
        } finally {}
    }

    // ==================== 查询 / 检索 / 搜索 ====================

    @Override
    public RiskbirdSearchResult search(String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        return search(config.getDefaultQueryType(), keyword, page);
    }

    /**
     * 按查询类型搜索（查公司/查老板/查风险/查文书/查关系）。
     * 混合双通道：API 优先抓响应，DOM 兜底解析列表。
     */
    public RiskbirdSearchResult search(RiskbirdConfig.QueryType type, String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        String channel = config.getQueryChannel();
        try {
            if ("api".equals(channel) || "hybrid".equals(channel)) {
                RiskbirdSearchResult apiResult = searchViaApi(type, keyword, page);
                if (apiResult != null && apiResult.isSuccess() && !apiResult.getCompanies().isEmpty()) {
                    return apiResult;
                }
            }
            if ("api".equals(channel)) {
                return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                        .error("API 通道未命中搜索结果").channel("api").build();
            }
            return searchViaDom(type, keyword, page);
        } finally {}
    }

    /**
     * 人员查询（查老板/人员电话查找的前置能力）：搜索人名，返回人员列表
     * （含关联企业数、地区分布、合作伙伴）。
     *
     * <p>真实页面：{@code /search/person?keyword=马云} → 「马云 | 共关联 39 家企业 | 浙江（共 14 家）… |
     * 合作伙伴 金建杭 合作9次」。人员详情页/接口含电话等联系方式。
     */
    public List<RiskbirdPerson> searchPersons(String personName, int maxResults)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try (ChromePage page = chromeBrowser.openPage(accountId)) {
            page.navigate(searchUrl(RiskbirdConfig.QueryType.PERSON, personName, 1));
            page.waitForLoadState(20);
            long deadline = System.currentTimeMillis() + config.getSearchTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                String bodyText = page.evalString("(document.body.innerText || '').slice(0, 3000)");
                if (bodyText != null && bodyText.contains(config.getLoginRequiredText())) {
                    return List.of();
                }
                if (bodyText != null && (bodyText.contains("共关联") || bodyText.contains("相关老板"))) {
                    break;
                }
                Thread.sleep(500);
            }
            // 从页面文本解析人员块（每人：姓名 + 共关联 N 家 + 地区分布 + 合作伙伴）
            String bodyText = page.evalString("(document.body.innerText || '').slice(0, 15000)");
            return parsePersons(personName, bodyText, maxResults);
        } finally {}
    }

    /**
     * 带筛选的企业检索（省份/地市/行业/状态）。
     *
     * <p>真实站点（2026-08-03 实测）：筛选条件不是 URL 参数（province= 无效），而是搜索页
     * 「省份地区」筛选项的页面交互——先导航搜索页，再点击筛选项触发查询，然后解析结果列表。
     * 本方法封装「导航 → 点击筛选 → 等待总数 → 解析结果」全流程。
     */
    public RiskbirdSearchResult search(RiskbirdConfig.QueryType type, String keyword, int page,
                                       RiskbirdSearchFilter filter)
            throws IOException, TimeoutException, InterruptedException {
        if (filter == null || !filter.hasAny()) {
            return search(type, keyword, page);
        }
        ensureContainer();
        // 关键修复：带 filter 时 keyword 命中 0 条（如采集传地区名"河南省"当企业名搜），
        // 自动换泛 keyword 重试——风鸟筛选是页面交互不是 URL 参数，泛 keyword + filter 驱动筛选检索。
        // 泅词表：风鸟站点按企业名搜，泛词能命中全国企业，筛选点击后缩小到目标地区/行业。
        java.util.List<String> fallbackKeywords = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) fallbackKeywords.add(keyword);
        fallbackKeywords.add("公司");
        fallbackKeywords.add("有限公司");
        for (String kw : fallbackKeywords) {
            try {
                RiskbirdSearchResult r = searchWithFilterRetry(type, kw, page, filter);
                if (r != null && r.isSuccess() && r.getTotal() != null && r.getTotal() > 0) {
                    return r;
                }
                // 命中 0 条或未命中：换下一个泸词重试（除非是登录拦截，那直接返回）
                if (r != null && r.getError() != null && r.getError().contains("未登录")) {
                    return r;
                }
                log.info("[RISKBIRD] keyword={} 命中 0 条，换泸词重试, type={}, page={}", kw, type, page);
            } catch (Exception e) {
                log.warn("[RISKBIRD] searchWithFilterRetry keyword={} 失败: {}", kw, e.getMessage());
            }
        }
        return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                .error("带筛选搜索未命中结果（所有泸词都命中 0 条，需换 keyword 或筛选条件）")
                .channel("dom").total(0).build();
    }

    private RiskbirdSearchResult searchWithFilterRetry(RiskbirdConfig.QueryType type, String keyword, int page,
                                                       RiskbirdSearchFilter filter)
            throws IOException, TimeoutException, InterruptedException {
        try (ChromePage pg = chromeBrowser.openPage(accountId)) {
            String navUrl = searchUrl(type, keyword, page);
            log.info("[RISKBIRD] search navigate: type={}, keyword={}, page={}, url={}", type, keyword, page, navUrl);
            pg.navigate(navUrl);
            // 关键兜底：prepareQrLogin 会清掉 Chrome profile cookie，采集复用同 profile 时
            // 风鸟按未登录搜命中 0 条。开页后立即注入 DB cookie 恢复登录态。
            injectCookiesIfNeeded(pg);
            pg.waitForLoadState(20);
            // 等 SPA hydration + 登录态稳定：宿主采集可能并发触发扫码重登（清 cookie + 重登），
            // 重登中 Chrome 的 userinfo-auth-btn 登录入口还在 → 此时跑筛选会命中失败。
            // 显式等登录入口消失（确认已登录态稳定），最多等 30 秒，再跑筛选交互。
            long loginStableDeadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < loginStableDeadline) {
                if (!pg.exists(config.getLoginEntrySelector())) {
                    break; // 登录入口消失 = 已登录态稳定
                }
                HumanDelay.sleep(1500, 2500);
            }
            // Nuxt SPA hydration 异步挂载筛选面板，waitForLoadState 只等网络空闲；
            // headless Chrome（服务器无 X server）hydration 比 headed 慢得多，实测 6 秒不够
            // filter-item-tag-item 渲染就绪 → 命中失败。显式等 8-12 秒让 hydration 完成，
            // 再等 filter-item-tag-item 出现，最后跑筛选交互。
            HumanDelay.sleep(8000, 12000);
            // 关键守门：先校验 navigate 命中条数 >0 才跑筛选。
            // 命中 0 条时筛选 DOM 根本不渲染 → applyFilter 全找不到 → fetched=0（真根因）。
            // 等待「总数」或「未命中提示」先到先得，最多等 searchTimeoutMs。
            Integer preTotal = null;
            long preDeadline = System.currentTimeMillis() + config.getSearchTimeoutMs();
            while (System.currentTimeMillis() < preDeadline) {
                String preBody = pg.evalString("(document.body.innerText || '').slice(0, 3000)");
                if (preBody != null && preBody.contains(config.getLoginRequiredText())) {
                    return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                            .error("未登录或查询次数已达上限，请先扫码登录").channel("dom").build();
                }
                preTotal = parseTotalCount(preBody);
                if (preTotal != null) break; // 解析到 total（含 0）即定型
                if (preBody != null && (preBody.contains("暂时没有找到") || preBody.contains("0 条相关"))) {
                    preTotal = 0; break;
                }
                Thread.sleep(500);
            }
            if (preTotal == null || preTotal <= 0) {
                return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                        .error("搜索 keyword 未命中任何企业（筛选无意义，需换 keyword）")
                        .channel("dom").total(preTotal == null ? 0 : preTotal).build();
            }
            // 筛选面板（filter-item-tag-item）是 Nuxt SPA 异步 hydration 渲染，
            // waitForLoadState 只等网络空闲，筛选选项可能尚未挂载 → applyFilter 命中失败。
            // 显式等 filter-item-tag-item 出现在 DOM 再交互（超时 30 秒，headless 更慢）。
            waitForExists(pg, ".filter-item-tag-item", 30_000);
            // 1. 页面点击筛选项（省份 → 地市 → 行业 → 状态）
            applyFilter(pg, filter);
            // 2. 轮询等待「总数」且结果项渲染完成（筛选后结果为 SPA 异步渲染，total 出现时卡片可能未就绪）
            Integer total = null;
            long deadline = System.currentTimeMillis() + config.getSearchTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                String bodyText = pg.evalString("(document.body.innerText || '').slice(0, 3000)");
                if (bodyText != null && bodyText.contains(config.getLoginRequiredText())) {
                    return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                            .error("未登录或查询次数已达上限，请先扫码登录").channel("dom").build();
                }
                total = parseTotalCount(bodyText);
                if (total != null && total > 0 && pg.count(config.getSearchResultItemSelector()) > 0) {
                    break;
                }
                Thread.sleep(500);
            }
            if (total == null || total <= 0) {
                return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                        .error("筛选后未命中结果（可能未登录、额度受限或无匹配企业）").channel("dom")
                        .total(total == null ? 0 : total).build();
            }
            // 3. 解析结果列表（total 出现但卡片未就绪时多等一轮）
            List<String> texts = pg.texts(config.getSearchResultItemSelector());
            if (texts.isEmpty()) {
                HumanDelay.sleep(1000, 2000);
                texts = pg.texts(config.getSearchResultItemSelector());
            }
            List<RiskbirdCompany> companies = new ArrayList<>();
            for (String t : texts) {
                if (t == null || t.contains("个人中心") || t.contains("扫码下载")
                        || t.trim().matches("\\d+")) { // 排除纯数字用户名（如「18268185209」）
                    continue;
                }
                String trimmed = t.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                RiskbirdCompany c = new RiskbirdCompany();
                c.setName(trimmed);
                c.setChannel("dom");
                companies.add(c);
            }
            RiskbirdSearchResult result = new RiskbirdSearchResult();
            result.setKeyword(keyword);
            result.setCompanies(companies);
            result.setTotal(total);
            result.setPage(page);
            result.setSuccess(!companies.isEmpty());
            result.setChannel("dom");
            return result;
        } finally {}
    }

    /** 在搜索页点击筛选项（省份 → 地市 → 行业 → 状态），每项点击后等待查询刷新。 */
    private void applyFilter(ChromePage pg, RiskbirdSearchFilter filter)
            throws IOException, TimeoutException, InterruptedException {
        if (filter.getProvince() != null && !filter.getProvince().isBlank()) {
            clickFilterItem(pg, filter.getProvince(), "省份");
            HumanDelay.sleep(900, 1800);
        }
        if (filter.getCity() != null && !filter.getCity().isBlank()) {
            clickFilterItem(pg, filter.getCity(), "地市");
            HumanDelay.sleep(900, 1800);
        }
        if (filter.getIndustry() != null && !filter.getIndustry().isBlank()) {
            clickFilterItem(pg, filter.getIndustry(), "行业");
            HumanDelay.sleep(900, 1800);
        }
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            clickFilterItem(pg, filter.getStatus(), "状态");
            HumanDelay.sleep(900, 1800);
        }
    }

    /**
     * 点击文本匹配的筛选项。
     * <p>真实站点（2026-08-11 实测 DOM）：筛选项渲染在
     * {@code <span class="filter-item-tag-item">河南 <span class="filter-item-tag-item-count">(10000+)</span></span>}
     * ——省名是短词（"河南"非"河南省"，"内蒙"非"内蒙古自治区"），计数是子 span 不参与匹配。
     * 本方法限定在 {@code filter-item-tag-item} 里取首个文本节点（排除计数子 span），用短词包含匹配。
     */
    private boolean clickFilterItem(ChromePage pg, String text, String section)
            throws IOException, TimeoutException {
        // 1. 限定在 filter-item-tag-item 里；2. 取该 span 的首个文本节点（排除计数子 span）；
        // 3. 短词包含：传入"河南省"→匹配选项"河南"（站点用短词），传入"在营"→匹配"在营"。
        boolean ok = pg.evalBool("(() => { "
                + "const want = " + esc(text) + ".trim(); "
                + "const items = Array.from(document.querySelectorAll('.filter-item-tag-item')); "
                + "const found = items.find(it => { "
                + "  const firstText = (it.firstChild && it.firstChild.nodeType === 3 ? it.firstChild.nodeValue : '').trim(); "
                + "  if (!firstText) return false; "
                + "  return firstText === want || firstText.includes(want) || want.includes(firstText); "
                + "}); "
                + "if (found) { found.click(); return true; } return false; })()");
        if (!ok) {
            log.warn("[RISKBIRD] 未找到筛选项: {}={}", section, text);
        } else {
            log.info("[RISKBIRD] 已点击筛选: {}={}", section, text);
        }
        return ok;
    }

    /**
     * 企业知识产权查询（商标/软著/专利）：进详情页 → 点击「知识产权」tab → 解析列表。
     *
     * <p>真实站点（2026-08-03 实测）：无独立商标/软著 URL，数据在详情页「知识产权」tab
     * （详情文本含「知识产权|999+」）；本方法点击 tab 后按区块解析商标/软著/专利条目。
     */
    public RiskbirdIntellectualProperty queryIntellectualProperty(String companyName, String entId)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try (ChromePage page = chromeBrowser.openPage(accountId)) {
            page.navigate(config.getEntUrlTemplate()
                    .replace("{company}", java.net.URLEncoder.encode(companyName, StandardCharsets.UTF_8))
                    .replace("{entid}", entId == null ? "" : entId));
            page.waitForLoadState(20);
            // 点击「知识产权」tab（contains 匹配，tab 文本可能带数字后缀如「知识产权|999+」）
            boolean clicked = page.evalBool("(() => { "
                    + "const els = Array.from(document.querySelectorAll('a,div,span,li')).filter(e => "
                    + "  (e.innerText||'').includes('知识产权') && e.children.length === 0); "
                    + "if (els.length > 0) { els[0].click(); return true; } return false; })()");
            if (!clicked) {
                log.warn("[RISKBIRD] 详情页未找到「知识产权」tab, company={}", companyName);
            }
            HumanDelay.sleep(2500, 4000); // 等知识产权区异步加载
            // 从「商标信息」数据区开始截取（tab 栏的「知识产权|999+」在数据区之前，定位到数据区更准确）
            String rawText = page.evalString("(() => { "
                    + "const t = document.body.innerText || ''; "
                    + "let i = t.indexOf('商标信息'); "
                    + "if (i < 0) { i = t.indexOf('知识产权'); } "
                    + "return i >= 0 ? t.substring(i, Math.min(t.length, i + 6000)) : ''; })()");
            return parseIntellectualProperty(rawText);
        } finally {}
    }

    /**
     * 按区块解析知识产权文本。
     *
     * <p>真实数据（2026-08-03 实测，点击「知识产权」tab 后 innerText）：
     * 「商标信息|13192|专利信息|8169|软件著作权|163|作品著作权|232|…」，
     * 表格行格式「序号|商标名称|国际分类|商标状态|申请注册号|申请日期|…」，
     * 行间以 {@code \n} 分隔、列以 {@code |} 或制表符分隔。
     */
    private RiskbirdIntellectualProperty parseIntellectualProperty(String rawText) {
        RiskbirdIntellectualProperty ip = new RiskbirdIntellectualProperty();
        if (rawText == null || rawText.isBlank()) {
            return ip;
        }
        ip.setRawText(rawText.length() > 2000 ? rawText.substring(0, 2000) : rawText);
        // 按区块切分：商标信息 / 软件著作权 / 专利信息
        String tmSection = sectionAfter(rawText, "商标信息", "专利信息");
        String softSection = sectionAfter(rawText, "软件著作权", "作品著作权");
        String patentSection = sectionAfter(rawText, "专利信息", "软件著作权");
        // 表格行：跳过表头与计数行，取「名称|分类|状态」形态
        ip.setTrademarks(parseTableRows(tmSection));
        ip.setSoftCopyrights(parseTableRows(softSection));
        ip.setPatents(parseTableRows(patentSection));
        return ip;
    }

    /** 截取 fromKeyword 之后、toKeyword 之前的文本（toKeyword 为空则到末尾）。 */
    private static String sectionAfter(String text, String fromKeyword, String toKeyword) {
        if (text == null) {
            return "";
        }
        int from = text.indexOf(fromKeyword);
        if (from < 0) {
            return "";
        }
        int end = text.length();
        if (toKeyword != null && !toKeyword.isEmpty()) {
            int to = text.indexOf(toKeyword, from + fromKeyword.length());
            if (to > from) {
                end = to;
            }
        }
        return text.substring(from + fromKeyword.length(), end);
    }

    /**
     * 解析表格行：每行「序号|名称|分类|状态|注册号|…」或制表符分隔，
     * 跳过表头（含「商标名称/国际分类/序号」等）与纯数字计数行，返回「名称(分类/状态/注册号)」条目。
     */
    private static List<String> parseTableRows(String section) {
        List<String> rows = new ArrayList<>();
        if (section == null || section.isBlank()) {
            return rows;
        }
        for (String line : section.split("\\n+")) {
            String t = line.trim();
            if (t.isEmpty() || t.length() < 3) {
                continue;
            }
            // 跳过表头/说明/计数行
            if (t.contains("商标名称") || t.contains("国际分类") || t.contains("商标状态")
                    || t.contains("申请注册号") || t.contains("点击进行搜索") || t.contains("导出")
                    || t.contains("序号") || t.contains("更多筛选") || t.contains("更多 ")) {
                continue;
            }
            // 按 | 或制表符拆列
            String[] cols = t.split("[|\\t]+");
            if (cols.length < 2) {
                continue;
            }
            // 第一列是序号则跳过序号，否则整行作为名称
            String name = cols.length >= 3 && isNumeric(cols[0]) ? cols[1] : cols[0];
            if (name == null || name.isBlank() || name.length() > 60) {
                continue;
            }
            // 附加分类/状态/注册号（如有）
            StringBuilder sb = new StringBuilder(name);
            for (int i = (cols.length >= 3 && isNumeric(cols[0]) ? 2 : 1); i < cols.length && i < 5; i++) {
                String c = cols[i].trim();
                if (!c.isEmpty() && !isNumeric(c) && !c.equals("详情")) {
                    sb.append(" (").append(c).append(')');
                }
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    private static boolean isNumeric(String s) {
        return s != null && s.matches("\\d+");
    }

    /** 解析人员列表（按「共关联 N 家企业」切块，取姓名/关联数/合作伙伴）。 */
    private List<RiskbirdPerson> parsePersons(String keyword, String bodyText, int maxResults) {
        List<RiskbirdPerson> persons = new ArrayList<>();
        if (bodyText == null || bodyText.isBlank()) {
            return persons;
        }
        int from = 0;
        java.util.regex.Pattern blockPattern = java.util.regex.Pattern
                .compile("([\\u4e00-\\u9fa5]{2,6})\\s*共关联\\s*(\\d+)\\s*家(?:企业)?");
        java.util.regex.Matcher m = blockPattern.matcher(bodyText);
        while (m.find() && persons.size() < maxResults) {
            String name = m.group(1);
            int count = Integer.parseInt(m.group(2));
            // 从匹配位置向后取一段作为详情（地区分布/合作伙伴）
            int end = bodyText.indexOf("共关联", m.start() + m.group(0).length());
            String snippet = bodyText.substring(m.start(), end < 0 ? Math.min(bodyText.length(), m.start() + 300) : end);
            RiskbirdPerson p = new RiskbirdPerson();
            p.setName(name);
            p.setCompanyCount(count);
            p.setRegionSummary(snippet.length() > 120 ? snippet.substring(0, 120) : snippet);
            // 合作伙伴：截取「合作伙伴」之后到下一个人员名之间
            int partnerIdx = snippet.indexOf("合作伙伴");
            if (partnerIdx >= 0) {
                String partnerText = snippet.substring(partnerIdx + 4, snippet.length());
                for (String line : partnerText.split("\\s+")) {
                    if (line.matches("^[\\u4e00-\\u9fa5]{2,6}(合作\\d+次)?")) {
                        p.getPartners().add(line);
                    }
                }
            }
            p.setChannel("dom");
            persons.add(p);
        }
        return persons;
    }

    @Override
    public void close() {
        // 容器生命周期由上层（ChromeBrowser / ChromeProfileManager）管理，驱动关闭不停止容器
    }

    @Override
    public RiskbirdCompany queryCompany(String companyName)
            throws IOException, TimeoutException, InterruptedException {
        // 先搜索拿到真实 entId（详情页需要 entid 参数才有数据）
        String entId = null;
        try {
            RiskbirdSearchResult search = search(RiskbirdConfig.QueryType.COMPANY, companyName, 1);
            if (search.isSuccess() && !search.getCompanies().isEmpty()) {
                entId = search.getCompanies().get(0).getEntId();
            }
        } catch (Exception e) {
            log.debug("[RISKBIRD] 查询详情前搜索失败(忽略), company={}, err={}", companyName, e.getMessage());
        }
        return queryCompany(companyName, entId);
    }

    /**
     * 按名称 + entId 查询企业详情（真实链路实测：详情页为文本布局，字段从页面 innerText 解析；
     * entId 必须传搜索结果中的 entid，否则详情页无数据）。
     */
    public RiskbirdCompany queryCompany(String companyName, String entId)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try {
            try (ChromePage page = chromeBrowser.openPage(accountId)) {
                page.navigate(config.getEntUrlTemplate()
                        .replace("{company}", java.net.URLEncoder.encode(companyName, StandardCharsets.UTF_8))
                        .replace("{entid}", entId == null ? "" : entId));
                page.waitForLoadState(20);
                // 详情页为文本布局（无标准 tr/li），从 innerText 正则解析关键字段
                String bodyText = page.evalString("(document.body.innerText || '').slice(0, 6000)");
                RiskbirdCompany company = parseDetailText(companyName, entId, bodyText);
                company.setDetailUrl(page.url());
                return company;
            }
        } catch (Exception e) {
            log.warn("[RISKBIRD] DOM 查询企业详情失败, company={}, err={}", companyName, e.getMessage());
            return RiskbirdCompany.builder().name(companyName).entId(entId).build();
        } finally {}
    }

    @Override
    public RiskbirdSearchResult retrieve(String keyword, int maxPages)
            throws IOException, TimeoutException, InterruptedException {
        RiskbirdSearchResult merged = new RiskbirdSearchResult();
        merged.setKeyword(keyword);
        merged.setSuccess(true);
        merged.setCompanies(new ArrayList<>());
        for (int p = 1; p <= Math.max(1, maxPages); p++) {
            RiskbirdSearchResult pageResult = search(keyword, p);
            if (!pageResult.isSuccess()) {
                merged.setError(pageResult.getError());
                break;
            }
            merged.getCompanies().addAll(pageResult.getCompanies());
            if (pageResult.getCompanies().isEmpty()) {
                break; // 无更多结果
            }
        }
        return merged;
    }

    // ==================== 内部：API 优先通道 ====================

    private RiskbirdSearchResult searchViaApi(RiskbirdConfig.QueryType type, String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try (CdpSession cdp = chromeBrowser.connectToAccount(accountId)) {
            ChromeNetwork net = new ChromeNetwork(cdp);
            net.enable(true); // 开启响应体捕获
            try (ChromePage pg = chromeBrowser.openPage(accountId)) {
                pg.navigate(searchUrl(type, keyword, page));
                injectCookiesIfNeeded(pg);
                pg.waitForLoadState(20);
            }
            // 等异步 XHR 到达并捕获
            HumanDelay.sleep(1200, 2500);
            // 遍历抓到的请求，找 URL 含关键字的接口，取其响应体解析（只处理 XHR/Fetch 类型）
            for (ChromeNetwork.RequestRecord r : net.snapshot()) {
                String rt = r.resourceType == null ? "" : r.resourceType;
                if (!"XHR".equalsIgnoreCase(rt) && !"Fetch".equalsIgnoreCase(rt) && !"Document".equalsIgnoreCase(rt)) {
                    continue; // 跳过 css/js/img 等
                }
                if (r.url != null && matchesAny(r.url, config.getSearchApiUrlKeywords())) {
                    String body = safeBody(net, r.requestId);
                    if (body != null && body.contains(type.path)) {
                        JsonNode raw = RiskbirdParser.parse(body);
                        if (raw != null) {
                            RiskbirdSearchResult result = RiskbirdParser.buildSearchResult(keyword, raw, "api");
                            log.info("[RISKBIRD] API 通道命中搜索结果, type={}, keyword={}, url={}, count={}",
                                    type.path, keyword, r.url, result.getCompanies().size());
                            return result;
                        }
                    }
                }
            }
            return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                    .error("API 通道未命中接口响应").channel("api").build();
        } finally {}
    }

    /** 安全读取响应体（资源可能已被回收/不可读，失败返回 null 不中断）。 */
    private String safeBody(ChromeNetwork net, String requestId) {
        try {
            return net.body(requestId);
        } catch (Exception e) {
            log.debug("[RISKBIRD] 读取响应体失败(忽略): {}", e.getMessage());
            return null;
        }
    }

    private RiskbirdSearchResult searchViaDom(RiskbirdConfig.QueryType type, String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        ensureContainer();
        try (ChromePage pg = chromeBrowser.openPage(accountId)) {
            pg.navigate(searchUrl(type, keyword, page));
            pg.waitForLoadState(20);
            // 未登录拦截提示是 SPA 异步渲染的，轮询等待「总数」或「未登录提示」先到先得
            Integer total = null;
            long deadline = System.currentTimeMillis() + config.getSearchTimeoutMs();
            while (System.currentTimeMillis() < deadline) {
                String bodyText = pg.evalString("(document.body.innerText || '').slice(0, 3000)");
                if (bodyText != null && bodyText.contains(config.getLoginRequiredText())) {
                    return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                            .error("未登录或查询次数已达上限，请先扫码登录").channel("dom").build();
                }
                total = parseTotalCount(bodyText);
                if (total != null) {
                    break;
                }
                Thread.sleep(500);
            }
            // 无结果（总数 0 或解析不到）：返回明确结果，避免把导航卡片误当搜索结果
            if (total == null || total <= 0) {
                return RiskbirdSearchResult.builder().keyword(keyword).success(false)
                        .error("未命中搜索结果（可能未登录、额度受限或该类型无结果）").channel("dom")
                        .total(0).build();
            }
            // 有结果：从真实企业卡片解析（排除导航「个人中心」等干扰项）
            List<String> texts = pg.texts(config.getSearchResultItemSelector());
            List<RiskbirdCompany> companies = new ArrayList<>();
            for (String t : texts) {
                if (t == null || t.contains("个人中心") || t.contains("扫码下载")
                        || t.trim().matches("\\d+")) { // 排除纯数字用户名（如「18268185209」）
                    continue;
                }
                String trimmed = t.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                RiskbirdCompany c = new RiskbirdCompany();
                c.setName(trimmed);
                c.setChannel("dom");
                companies.add(c);
            }
            RiskbirdSearchResult result = new RiskbirdSearchResult();
            result.setKeyword(keyword);
            result.setCompanies(companies);
            result.setTotal(total);
            result.setPage(page);
            result.setSuccess(!companies.isEmpty());
            if (companies.isEmpty()) {
                result.setError("结果页有总数但未解析到结果项（选择器需校准）");
            }
            result.setChannel("dom");
            return result;
        } finally {}
    }

    /** 从页面文本解析总数（「为您找到 N 条相关结果」/「共 N 条」）。 */
    private static Integer parseTotalCount(String bodyText) {
        if (bodyText == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("为您找到\\s*([\\d,]+)\\s*条|共\\s*([\\d,]+)\\s*条")
                .matcher(bodyText);
        if (m.find()) {
            String num = m.group(1) != null ? m.group(1) : m.group(2);
            if (num != null) {
                try {
                    return Integer.parseInt(num.replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 详情页文本解析（真实链路实测：详情页为文本布局，字段以「法定代表人：蒋芳」「统一社会信用代码：...」
     * 「成立日期 2007-03-26」「注册资本 15460.55039万美元」「注册地址 ...」形式出现）。
     */
    private RiskbirdCompany parseDetailText(String name, String entId, String bodyText) {
        RiskbirdCompany company = new RiskbirdCompany();
        company.setName(name);
        company.setEntId(entId);
        if (bodyText == null || bodyText.isBlank()) {
            return company;
        }
        company.setLegalPerson(grabAfter(bodyText, "法定代表人"));
        company.setCreditCode(grabAfter(bodyText, "统一社会信用代码"));
        company.setEstablishDate(grabAfter(bodyText, "成立日期"));
        company.setRegisteredCapital(grabAfter(bodyText, "注册资本"));
        company.setAddress(grabAfter(bodyText, "注册地址"));
        // 联系方式（详情页文本含「电话：13482393468」「邮箱：gsll@service.alibaba.com」；
        // 排除页脚「客服电话：18911918041」「客服邮箱：contact@riskbird.com」干扰）
        String phone = grabAfter(bodyText, "电话");
        company.setPhone(phone == null || phone.contains("客服") ? null : phone);
        String email = grabAfter(bodyText, "邮箱");
        company.setEmail(email == null || email.contains("客服") ? null : email);
        // 状态：详情页通常显示「在营/正常」等标签
        company.setStatus(grabStatus(bodyText));
        company.setChannel("dom");
        return company;
    }

    /** 取「关键字」后一段文本（到下一个中文字段/换行/分隔符为止），找不到返回 null。 */
    private static String grabAfter(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) {
            return null;
        }
        int start = idx + keyword.length();
        // 跳过「：」等分隔符
        while (start < text.length() && (text.charAt(start) == '：' || text.charAt(start) == ':'
                || text.charAt(start) == ' ' || text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
            start++;
        }
        int end = start;
        // 读到下一个中文冒号/中文字段名（长度≥2的汉字簇）或换行为止
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '\n' || c == '\r') {
                break;
            }
            // 遇到「XX：」形态的下一个字段（冒号前是中文）
            if (c == '：') {
                // 检查冒号前是否为字段名（2-6个汉字）
                String prefix = text.substring(Math.max(start, end - 6), end);
                if (prefix.matches(".*[\\u4e00-\\u9fa5]{2,6}$")) {
                    break;
                }
            }
            end++;
        }
        String value = text.substring(start, end).trim();
        return value.isEmpty() ? null : value;
    }

    /** 从文本中提取登记状态（在营/注销/吊销/存续等）。 */
    private static String grabStatus(String text) {
        for (String s : new String[]{"在营", "存续", "注销", "吊销", "迁出", "撤销"}) {
            int idx = text.indexOf(s);
            if (idx >= 0) {
                return s;
            }
        }
        return null;
    }

    private boolean matchesAny(String url, String[] keywords) {
        if (keywords == null) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && url.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /** 字符串 → JSON 字符串字面量（带引号，防注入）。 */
    private static String esc(String s) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(s == null ? "" : s);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    /**
     * 构建真实搜索 URL（实测格式）：{@code https://www.riskbird.com/search/{type}?keyword={kw}&timestamp={ts}}
     * 如查公司：{@code /search/company?keyword=阿里巴巴&timestamp=1785738879138}。
     */
    private String searchUrl(RiskbirdConfig.QueryType type, String keyword, int page) {
        try {
            String kw = java.net.URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8);
            String base = config.getBaseUrl().replaceAll("/+$", "");
            return base + "/search/" + type.path + "?keyword=" + kw + "&timestamp=" + System.currentTimeMillis();
        } catch (Exception e) {
            return config.getBaseUrl();
        }
    }

    // ==================== 内部：容器与登录态 ====================

    /**
     * 检测当前页面是否已登录态。
     * <p>
     * 复用 profile 的 Chrome 可能停在登录后页（{@code userinfo-auth-btn} 登录入口消失），
     * 此时再调 {@link #prepareQrLogin()} 会因等不到登录入口而超时。
     * <p>
     * 判定依据（任一命中即视为已登录）：
     * <ul>
     *   <li>URL 含 {@link RiskbirdConfig#getLoginSuccessUrlKeywords() 登录成功关键字}之一</li>
     *   <li>document.cookie 含非空 {@code riskbird} 或 {@code session} 类 token</li>
     *   <li>页面上不存在登录入口选择器（已登录态会隐藏登录/注册按钮）</li>
     * </ul>
     */
    private boolean isLoggedIn(ChromePage page) throws IOException, TimeoutException {
        // 1. URL 关键字
        String url = page.url();
        if (url != null) {
            for (String kw : config.getLoginSuccessUrlKeywords()) {
                if (url.contains(kw)) {
                    return true;
                }
            }
        }
        // 2. cookie 含登录 token（非空且非匿名 placeholder）
        String cookie = page.evalString("document.cookie || ''");
        if (cookie != null && !cookie.isBlank()) {
            // 风鸟登录态 cookie 含 riskbird_token / session / token 等关键字
            String lower = cookie.toLowerCase();
            if (lower.contains("riskbird_token") || lower.contains("session")
                    || (lower.contains("token") && !lower.contains("token="))) {
                return true;
            }
        }
        // 3. 登录入口不存在于 DOM（已登录态会隐藏登录/注册按钮）
        try {
            return !page.exists(config.getLoginEntrySelector());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 兜底注入账号登录态 cookie（prepareQrLogin 清掉 Chrome profile cookie 后恢复登录态）。
     * 通过 document.cookie 逐个写入 accountCookieHeader 解析出的键值对。
     * 仅在 accountCookieHeader 非空时注入，避免空注入打断未登录态。
     */
    private void injectCookiesIfNeeded(ChromePage pg) throws IOException, TimeoutException {
        if (accountCookieHeader == null || accountCookieHeader.isBlank()) {
            return;
        }
        try {
            StringBuilder js = new StringBuilder("(function(){var pairs=");
            // 用 JSON.stringify 把 cookie header 转成 JS 字符串字面量，避免引号转义陷阱
            js.append(pg.evalString("JSON.stringify('" + accountCookieHeader.replace("'", "\\'") + "')"));
            js.append(".split(';');var ok=0;for(var i=0;i<pairs.length;i++){var p=pairs[i].trim();var idx=p.indexOf('=');if(idx>0){var n=p.substring(0,idx).trim();var v=p.substring(idx+1).trim();if(n){document.cookie=n+'='+v+';path=/;max-age=86400';ok++;}}}return 'injected='+ok;})()");
            String result = pg.evalString(js.toString());
            log.info("[RISKBIRD] injectCookiesIfNeeded: {}, accountId={}", result, accountId);
        } catch (Exception e) {
            log.warn("[RISKBIRD] injectCookiesIfNeeded 失败: {}, accountId={}", e.getMessage(), accountId);
        }
    }

    /**
     * 清除风鸟站点登录 cookie（让 Chrome 回到未登录态）。
     * <p>
     * 通过 {@code document.cookie} 逐个置过期删除。风鸟登录态靠 cookie（非 localStorage），
     * 清 cookie 即可退出。清后需 {@code navigate} 重新加载页面让站点回到未登录首页。
     */
    private void clearRiskbirdCookies(ChromePage page) throws IOException, TimeoutException {
        // 遍历 document.cookie 里的每个 cookie，逐个置过期（max-age=0）删除。
        // 风鸟域是 www.riskbird.com，path=/，故 domain/path 固定。
        // 用 ChromeNetwork 通道不可行（chrome devtools 协议清 cookie 需 Network.deleteCookies），
        // 改用一行紧凑 JS（避免 Java 字符串拼接里的嵌套引号转义陷阱）。
        String clearJs = "(function(){var d='.riskbird.com';var p='/';var c=document.cookie||'';"
                + "var ps=c.split(';').map(function(s){return s.trim();});"
                + "var n=0;for(var i=0;i<ps.length;i++){var k=ps[i].split('=')[0].trim();"
                + "if(!k)continue;"
                + "document.cookie=k+'=; expires=Thu, 01 Jan 1970 00:00:00 GMT; domain='+d+'; path='+p;"
                + "document.cookie=k+'=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path='+p;"
                + "n++;}"
                + "return 'cleared='+n+', remaining='+(document.cookie||'').length;})()";
        String result = page.evalString(clearJs);
        log.info("[RISKBIRD] 清 cookie 结果: {}, accountId={}", result, accountId);
    }

    /**
     * 轮询等待元素「存在于 DOM」（不要求可见）。
     * <p>
     * 风鸟登录入口 / 二维码 img 在 Element UI popover 内，popover 容器隐藏（0 尺寸），
     * {@code offsetParent === null}；Nuxt SSR hydration 早期也会短暂出现这种情况。
     * 故不能复用 {@code ChromePage#waitForSelector}（其语义是 {@code waitForVisible}），
     * 否则会误判为「等待元素可见超时」。
     *
     * @param page      Chrome 页面句柄
     * @param selector  CSS 选择器
     * @param timeoutMs 超时（毫秒）
     * @throws TimeoutException 超时仍未出现在 DOM
     */
    private void waitForExists(ChromePage page, String selector, long timeoutMs)
            throws IOException, TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (page.exists(selector)) {
                return;
            }
            HumanDelay.sleep(150, 250);
        }
        // 超时不抛异常——降级让调用方继续尝试（applyFilter 仍可能命中已渲染但晚到的选项）
        log.warn("[RISKBIRD] 等待元素存在超时（降级继续）: {} ({}ms)", selector, timeoutMs);
    }

    /** 确保账号对应的 Chrome 容器就绪（容器生命周期由上层 ChromeBrowser/ChromeProfileManager 管理）。 */
    private void ensureContainer() {
        chromeBrowser.requireProfile(accountId);
    }

    /** 构造登录结果（统一填 accountId；username/cookieHeader 按场景传入，不适用的传 null）。 */
    private RiskbirdLoginResult loginResult(boolean success, String username, String cookieHeader, String message) {
        return RiskbirdLoginResult.builder()
                .success(success)
                .accountId(accountId)
                .username(username)
                .cookieHeader(cookieHeader)
                .message(message)
                .build();
    }

    /** 在 Chrome 会话内把已渲染的二维码 <img> 转成 base64 data URL（前端可直接 <img> 显示）。 */
    private String fetchQrAsDataUrl(ChromePage page, String qrUrl) {
        if (qrUrl == null || qrUrl.isBlank()) return null;
        try {
            // 用 canvas 从页面已加载的二维码 img 提取 base64（同会话已具备访问权限，
            // 避免 fetch 二次请求的 CORS/401 问题；单行 JS 避免多行脚本语法风险）
            String js = "(() => { const img = document.querySelector('img.xs-login-left-qrcode');"
                    + " if (!img || !img.complete || img.naturalWidth === 0) return '';"
                    + " const c = document.createElement('canvas'); c.width = img.naturalWidth; c.height = img.naturalHeight;"
                    + " c.getContext('2d').drawImage(img, 0, 0);"
                    + " try { return c.toDataURL('image/png'); } catch (e) { return ''; } })()";
            String dataUrl = page.evalString(js);
            if (dataUrl != null && dataUrl.startsWith("data:image")) {
                log.info("[RISKBIRD] 二维码图片已转为 data URL, length={}", dataUrl.length());
                return dataUrl;
            }
            log.warn("[RISKBIRD] 二维码图片转 data URL 失败, 回退原始 URL");
        } catch (Exception e) {
            log.warn("[RISKBIRD] 二维码图片转 data URL 异常, 回退原始 URL: {}", e.getMessage());
        }
        return null;
    }
}
