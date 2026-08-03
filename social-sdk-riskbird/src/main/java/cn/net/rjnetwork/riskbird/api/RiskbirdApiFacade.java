package cn.net.rjnetwork.riskbird.api;

import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdIntellectualProperty;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchFilter;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Riskbird API 门面：登录 / 查询 / 检索 / 搜索的统一入口。
 *
 * <p>每个实例绑定一个账号（由 {@link RiskbirdSdk} 按 accountId 隔离创建），
 * 内部委托给 {@link RiskbirdPageDriver}（默认 {@link ChromeRiskbirdDriver}，每账号独立容器）。
 */
public class RiskbirdApiFacade {

    private final RiskbirdConfig config;
    private final RiskbirdPageDriver driver;

    public RiskbirdApiFacade(RiskbirdConfig config, RiskbirdPageDriver driver) {
        this.config = config;
        this.driver = driver;
    }

    // ==================== 登录 ====================

    /** 账号密码登录。 */
    public RiskbirdLoginResult loginWithPassword(String username, String password)
            throws IOException, TimeoutException, InterruptedException {
        return driver.loginWithPassword(username, password);
    }

    /** 扫码登录：返回二维码图片 base64 / data URL，调用方展示后轮询 {@link #waitQrLogin(String)}。 */
    public String prepareQrLogin() throws IOException, TimeoutException, InterruptedException {
        return driver.prepareQrLogin();
    }

    /** 等待扫码完成。 */
    public RiskbirdLoginResult waitQrLogin(String qrSession)
            throws IOException, TimeoutException, InterruptedException {
        return driver.waitQrLogin(qrSession);
    }

    /** Cookie 免登录。 */
    public RiskbirdLoginResult loginWithCookie(String cookieHeader) throws IOException, TimeoutException {
        return driver.loginWithCookie(cookieHeader);
    }

    /** 是否已登录。 */
    public boolean isLoggedIn() throws IOException, TimeoutException {
        return driver.isLoggedIn();
    }

    /** 提取当前登录态 Cookie（持久化复用）。 */
    public String extractCookieHeader() throws IOException, TimeoutException {
        return driver.extractCookieHeader();
    }

    // ==================== 查询 / 检索 / 搜索 ====================

    /** 关键词搜索（第 page 页，1 起）。 */
    public RiskbirdSearchResult search(String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        return driver.search(keyword, page);
    }

    /** 按查询类型搜索（查公司/查老板/查风险/查文书/查关系）。 */
    public RiskbirdSearchResult search(RiskbirdConfig.QueryType type, String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        if (driver instanceof ChromeRiskbirdDriver chromeDriver) {
            return chromeDriver.search(type, keyword, page);
        }
        // 非 Chrome 驱动（mock 等）无类型重载时回落默认类型
        return driver.search(keyword, page);
    }

    /**
     * 带筛选的企业检索（省份/地市/行业/状态）。
     * 业务场景：按省份/地市检索某类企业列表（含电话，电话在企业详情字段 phone）。
     */
    public RiskbirdSearchResult search(RiskbirdConfig.QueryType type, String keyword, int page,
                                       RiskbirdSearchFilter filter)
            throws IOException, TimeoutException, InterruptedException {
        if (driver instanceof ChromeRiskbirdDriver chromeDriver) {
            return chromeDriver.search(type, keyword, page, filter);
        }
        return driver.search(keyword, page);
    }

    /**
     * 企业知识产权查询（商标/软著/专利）：进详情页 → 点击「知识产权」tab → 解析列表。
     *
     * @param companyName 企业名称
     * @param entId       搜索结果中的 entId（必传，详情页数据依赖）
     */
    public RiskbirdIntellectualProperty queryIntellectualProperty(String companyName, String entId)
            throws IOException, TimeoutException, InterruptedException {
        if (driver instanceof ChromeRiskbirdDriver chromeDriver) {
            return chromeDriver.queryIntellectualProperty(companyName, entId);
        }
        return new RiskbirdIntellectualProperty();
    }

    /** 查询企业详情（按名称，内部先搜索拿 entId）。 */
    public RiskbirdCompany queryCompany(String companyName)
            throws IOException, TimeoutException, InterruptedException {
        return driver.queryCompany(companyName);
    }

    /** 查询企业详情（按名称 + entId，直接访问详情页；entId 来自搜索结果）。 */
    public RiskbirdCompany queryCompany(String companyName, String entId)
            throws IOException, TimeoutException, InterruptedException {
        if (driver instanceof ChromeRiskbirdDriver chromeDriver) {
            return chromeDriver.queryCompany(companyName, entId);
        }
        return driver.queryCompany(companyName);
    }

    /** 检索（多页聚合）。 */
    public RiskbirdSearchResult retrieve(String keyword, int maxPages)
            throws IOException, TimeoutException, InterruptedException {
        return driver.retrieve(keyword, maxPages);
    }

    /**
     * 人员查询（查老板/人员电话查找前置能力）：搜索人名，返回人员列表
     * （含关联企业数、地区分布、合作伙伴）。
     */
    public java.util.List<cn.net.rjnetwork.riskbird.model.RiskbirdPerson> searchPersons(String personName, int maxResults)
            throws IOException, TimeoutException, InterruptedException {
        if (driver instanceof ChromeRiskbirdDriver chromeDriver) {
            return chromeDriver.searchPersons(personName, maxResults);
        }
        return java.util.List.of();
    }

    /** 商标查询（需具体商标名称）。 */
    public RiskbirdSearchResult searchTrademark(String keyword, int page)
            throws IOException, TimeoutException, InterruptedException {
        return search(RiskbirdConfig.QueryType.TRADEMARK, keyword, page);
    }

    /** 底层驱动（测试/扩展用）。 */
    public RiskbirdPageDriver driver() {
        return driver;
    }

    /** 关闭账号会话（释放资源）。 */
    public void close() {
        try {
            driver.close();
        } catch (Exception e) {
            // 忽略关闭异常
        }
    }
}
