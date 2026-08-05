package cn.net.rjnetwork.riskbird.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果（含登录态与容器状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskbirdLoginResult {

    /** 是否登录成功。 */
    private boolean success;

    /** 账号 ID（容器隔离的键）。 */
    private Long accountId;

    /** 账号标识（用户名/手机号，扫码登录无原始用户名时为 null）。 */
    private String username;

    /** 登录成功后提取的 Cookie（cookie header 形式，可持久化复用）；失败或未提取时为 null。 */
    private String cookieHeader;

    /** 消息（成功提示 / 失败原因）。 */
    private String message;
}
