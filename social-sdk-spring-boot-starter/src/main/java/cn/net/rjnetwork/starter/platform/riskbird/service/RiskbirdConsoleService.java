package cn.net.rjnetwork.starter.platform.riskbird.service;

import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCompany;
import cn.net.rjnetwork.riskbird.model.RiskbirdIntellectualProperty;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.riskbird.model.RiskbirdPerson;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchFilter;
import cn.net.rjnetwork.riskbird.model.RiskbirdSearchResult;
import cn.net.rjnetwork.riskbird.service.RiskbirdSdk;
import cn.net.rjnetwork.starter.platform.riskbird.config.RiskbirdConsoleProperties;
import cn.net.rjnetwork.starter.platform.riskbird.dto.RiskbirdBizQueryRequest;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * riskbird REST 能力服务：把 {@link RiskbirdSdk} 的多账户隔离 + 查询能力暴露给 Controller。
 *
 * <p>每账号（accountId）独立 Chrome 容器（复用 social-sdk-chrome），账号间互不干扰。
 */
public class RiskbirdConsoleService {

    private static final Logger log = LoggerFactory.getLogger(RiskbirdConsoleService.class);

    private final RiskbirdConsoleProperties properties;
    private final RiskbirdSdk sdk;

    public RiskbirdConsoleService(RiskbirdConsoleProperties properties, ChromeBrowser chromeBrowser) {
        this.properties = properties;
        RiskbirdConfig config = new RiskbirdConfig();
        if (properties.getQueryChannel() != null && !properties.getQueryChannel().isBlank()) {
            config.setQueryChannel(properties.getQueryChannel());
        }
        if (properties.getSearchTimeoutMs() > 0) {
            config.setSearchTimeoutMs(properties.getSearchTimeoutMs());
        }
        config.setPerAccountContainer(properties.isPerAccountContainer());
        // 默认查询类型
        try {
            config.setDefaultQueryType(RiskbirdConfig.QueryType.valueOf(
                    properties.getDefaultQueryType().toUpperCase()));
        } catch (Exception e) {
            log.warn("非法 default-query-type: {}，使用默认 company", properties.getDefaultQueryType());
        }
        this.sdk = new RiskbirdSdk(config, chromeBrowser);
    }

    /** 健康检查（返回账号容器配置摘要）。 */
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "enabled", properties.isEnabled(),
                "queryChannel", properties.getQueryChannel(),
                "perAccountContainer", properties.isPerAccountContainer());
    }

    /** 获取账号门面（懒创建，多账户隔离）。 */
    private RiskbirdApiFacade api(long accountId) {
        return sdk.account(accountId).api();
    }

    /** Cookie 登录（免扫码；需 token + userinfo 的已登录 Cookie）。 */
    public RiskbirdLoginResult loginWithCookie(long accountId, String cookieHeader) throws Exception {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            cookieHeader = properties.getCookieHeader();
        }
        if (cookieHeader == null || cookieHeader.isBlank()) {
            throw new IllegalArgumentException("cookieHeader 不能为空（可配置 social-sdk.console.riskbird.cookie-header 预置）");
        }
        return api(accountId).loginWithCookie(cookieHeader);
    }

    /** 搜索（含商标/模糊查询：queryType 决定五类入口 + trademark/person）。 */
    public RiskbirdSearchResult search(long accountId, String keyword, String queryType, int page) throws Exception {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword 不能为空");
        }
        RiskbirdConfig.QueryType type = resolveQueryType(queryType);
        return api(accountId).search(type, keyword, Math.max(1, page));
    }

    /** 人员查询（人员电话查找前置能力）：返回人员列表（关联企业数/地区/合作伙伴）。 */
    public List<RiskbirdPerson> searchPersons(long accountId, String name, int maxResults) throws Exception {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        return api(accountId).searchPersons(name, Math.max(1, maxResults));
    }

    /** 企业详情（按名称，自动先搜索拿 entId）。 */
    public RiskbirdCompany queryCompany(long accountId, String companyName) throws Exception {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName 不能为空");
        }
        return api(accountId).queryCompany(companyName);
    }

    /** 企业详情（按名称 + entId，含电话/邮箱等字段）。 */
    public RiskbirdCompany queryCompany(long accountId, String companyName, String entId) throws Exception {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName 不能为空");
        }
        return api(accountId).queryCompany(companyName, entId);
    }

    /** 是否已登录。 */
    public boolean isLoggedIn(long accountId) throws Exception {
        return api(accountId).isLoggedIn();
    }

    /** 提取登录态 Cookie（持久化复用）。 */
    public String extractCookieHeader(long accountId) throws Exception {
        return api(accountId).extractCookieHeader();
    }

    /** 关闭账号会话（释放容器资源）。 */
    public void closeAccount(long accountId) {
        sdk.closeAccount(accountId);
    }

    /** 解析 queryType 字符串 → 枚举。 */
    private RiskbirdConfig.QueryType resolveQueryType(String queryType) {
        if (queryType == null || queryType.isBlank()) {
            return RiskbirdConfig.QueryType.COMPANY;
        }
        try {
            return RiskbirdConfig.QueryType.valueOf(queryType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法 queryType: " + queryType
                    + "，可选: company/boss/risk/wenshu/relation/trademark/person");
        }
    }
}
