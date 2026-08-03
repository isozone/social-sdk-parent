package cn.net.rjnetwork.xianyu.chrome.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号浏览器登录态快照（Cookie + localStorage + sessionStorage）。
 *
 * <p>用途：
 * <ul>
 *   <li>跨容器迁移：把 A 账号容器的登录态导出，导入到 B 容器（换代理/换机器时保登录态）</li>
 *   <li>断线重登恢复：容器崩溃重启后，用最近一次快照快速恢复登录态，避免重新扫码/短信</li>
 *   <li>离线备份：定时落盘，异常时回滚</li>
 * </ul>
 *
 * <p>注意：快照含敏感凭证（cookie value），存储/传输需按业务侧安全规范处理（建议加密落盘）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSnapshot {

    /** 归属账号 ID。 */
    private Long accountId;

    /** 快照创建时间（毫秒时间戳）。 */
    private Long createdAt;

    /** 快照来源 URL（导出时的页面地址，导入时按需导航）。 */
    private String sourceUrl;

    /** Cookie 列表（可完整还原 httpOnly / secure / sameSite）。 */
    @Builder.Default
    private List<CookieData> cookies = new ArrayList<>();

    /** localStorage 键值（按域名原样保留）。 */
    @Builder.Default
    private Map<String, String> localStorage = new LinkedHashMap<>();

    /** sessionStorage 键值（sessionStorage 跨页面会话共享，导入后立即可用）。 */
    @Builder.Default
    private Map<String, String> sessionStorage = new LinkedHashMap<>();

    /** 单条 Cookie 描述（还原 {@code Network.setCookies} 所需字段）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CookieData {
        private String name;
        private String value;
        private String domain;
        private String path;
        private Double expires;
        private Boolean httpOnly;
        private Boolean secure;
        private String sameSite;
    }
}
