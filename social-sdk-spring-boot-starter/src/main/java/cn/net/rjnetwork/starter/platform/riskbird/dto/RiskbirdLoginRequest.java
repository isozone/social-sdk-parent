package cn.net.rjnetwork.starter.platform.riskbird.dto;

/**
 * 登录请求（Cookie 免登录；账号密码/扫码见 README）。
 */
public class RiskbirdLoginRequest {

    /** 账号 ID（每账号独立容器的隔离键）。 */
    private Long accountId;

    /** 已登录 Cookie（k1=v1; k2=v2），含 token/userinfo 即视为登录态。 */
    private String cookieHeader;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }
}
