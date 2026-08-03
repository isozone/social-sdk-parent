package cn.net.rjnetwork.starter.platform.riskbird.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * riskbird 控制台能力配置。
 */
@ConfigurationProperties(prefix = "social-sdk.console.riskbird")
public class RiskbirdConsoleProperties {

    /** 是否启用 riskbird REST 能力。 */
    private boolean enabled = false;

    /** 查询通道：api / dom / hybrid（默认 hybrid = API 优先 + DOM 兜底）。 */
    private String queryChannel = "hybrid";

    /** 每账号独立 Chrome 容器（复用 social-sdk-chrome 的容器隔离）。 */
    private boolean perAccountContainer = true;

    /** 默认查询类型：company / boss / risk / wenshu / relation / trademark / person。 */
    private String defaultQueryType = "company";

    /** 搜索超时（毫秒）。 */
    private long searchTimeoutMs = 15_000;

    /** 登录态 Cookie（可选预置：REST 调用前可先注入已登录 Cookie 免扫码）。 */
    private String cookieHeader = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQueryChannel() {
        return queryChannel;
    }

    public void setQueryChannel(String queryChannel) {
        this.queryChannel = queryChannel;
    }

    public boolean isPerAccountContainer() {
        return perAccountContainer;
    }

    public void setPerAccountContainer(boolean perAccountContainer) {
        this.perAccountContainer = perAccountContainer;
    }

    public String getDefaultQueryType() {
        return defaultQueryType;
    }

    public void setDefaultQueryType(String defaultQueryType) {
        this.defaultQueryType = defaultQueryType;
    }

    public long getSearchTimeoutMs() {
        return searchTimeoutMs;
    }

    public void setSearchTimeoutMs(long searchTimeoutMs) {
        this.searchTimeoutMs = searchTimeoutMs;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }
}
