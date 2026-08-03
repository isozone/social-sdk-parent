package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号登录凭证（账号密码 / Cookie 两种形态）。
 *
 * <p>账号密码用于走页面表单登录；cookie 用于直接注入登录态（免登录）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdCredentials {

    /** 账号标识（用户名 / 手机号）。 */
    private String username;

    /** 登录密码（仅在账号密码登录时使用）。 */
    private String password;

    /** 已登录的 Cookie（cookie header 形式，如 k1=v1; k2=v2），与 username/password 二选一。 */
    private String cookieHeader;
}
