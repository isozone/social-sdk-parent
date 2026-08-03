package cn.net.rjnetwork.riskbird.config;

import lombok.Data;

/**
 * Riskbird 平台配置（绑定前缀 riskbird）。
 *
 * <p>覆盖站点入口、登录方式、查询通道（API 优先 / DOM 兜底）与每账号容器隔离策略。
 */
@Data
public class RiskbirdConfig {

    /** 站点首页。 */
    private String baseUrl = "https://www.riskbird.com/";

    /** 登录页入口（站内登录弹窗/页面的路由）。 */
    private String loginUrl = "https://www.riskbird.com/login";

    /** 企业详情页 URL 模板（{company} 占位公司名，{entid} 占位企业 ID）。 */
    private String entUrlTemplate = "https://www.riskbird.com/ent/{company}.html?entid={entid}";

    /** 查询通道：api = 优先抓登录后 XHR/API 响应；dom = 纯页面 DOM 解析；hybrid = API 优先 + DOM 兜底。 */
    private String queryChannel = "hybrid";

    /** 搜索时等待结果出现的超时（毫秒）。 */
    private long searchTimeoutMs = 15_000;

    /** 登录成功判定超时（毫秒）。 */
    private long loginTimeoutMs = 60_000;

    /** 扫码登录轮询间隔（毫秒）。 */
    private long qrPollIntervalMs = 2_000;

    /** 是否在容器启动时注入反检测指纹脚本。 */
    private boolean antiDetectEnabled = true;

    /** 是否在容器启动时应用增强指纹（时区/音频/字体等）。 */
    private boolean enhancedFingerprintEnabled = true;

    /** 登录态快照是否落盘到 profile 目录（重启后自动恢复）。 */
    private boolean snapshotPersistenceEnabled = true;

    // ===== 登录页选择器（已按真实站点结构实测校准 2026-08-03）=====

    /** 首页「登录/注册」入口（真实 class: userinfo-auth-btn-gohst）。 */
    private String loginEntrySelector = "[class*=userinfo-auth-btn]";

    /** 登录弹窗内「登录试试」按钮（点击后出现扫码二维码）。 */
    private String loginTryButtonSelector = ".popover-btn";

    /** 扫码二维码图片（真实：img.xs-login-left-qrcode，src 为 /riskbird-api/createQrCode?uuid=xxx）。 */
    private String qrImageSelector = "img.xs-login-left-qrcode";

    /** 扫码登录成功后跳转的目标页（检测登录态时校验）。 */
    private String loginSuccessUrl = "https://www.riskbird.com/";

    /** 登录成功特征：URL 包含这些关键字之一即视为已登录。 */
    private String[] loginSuccessUrlKeywords = {"dashboard", "member", "home", "index"};

    /** 二维码接口 URL 关键字（抓包识别登录态轮询接口用）。 */
    private String[] qrApiUrlKeywords = {"createQrCode", "qr", "scan"};

    // ===== 查询页选择器 / 关键字（已按真实站点结构实测校准 2026-08-03）=====

    /** 查询类型（对应站点五类入口：查公司/查老板/查风险/查文书/查关系 + 扩展能力）。 */
    public enum QueryType {
        /** 查公司：/search/company?keyword=（支持企业名模糊/部分匹配，实测短词可命中） */
        COMPANY("company", "查公司"),
        /** 查老板：/search/boss?keyword=（搜人名，如「马云」） */
        BOSS("boss", "查老板"),
        /** 查风险：/search/risk?keyword= */
        RISK("risk", "查风险"),
        /** 查文书：/search/wenshu?keyword= */
        WENSHU("wenshu", "查文书"),
        /** 查关系：/search/relation?keyword= */
        RELATION("relation", "查关系"),
        /** 商标查询：/search/trademark?keyword=（需具体商标名称，路由实测有效） */
        TRADEMARK("trademark", "商标"),
        /** 人员查询：/search/person?keyword=（搜人名，接口 /riskbird-api/api/v1/persons/search 返回 JSON，含关联企业/合作伙伴） */
        PERSON("person", "人员");

        /** URL 路径段。 */
        public final String path;
        /** 站点入口显示名。 */
        public final String label;

        QueryType(String path, String label) {
            this.path = path;
            this.label = label;
        }
    }

    /** 默认查询类型（查公司）。 */
    private QueryType defaultQueryType = QueryType.COMPANY;

    /** 搜索接口 URL 关键字（ChromeNetwork 抓包匹配，命中即解析为搜索结果）。 */
    private String[] searchApiUrlKeywords = {"search", "query", "ent", "company"};

    /** 搜索结果列表项选择器（DOM 兜底通道；实测结果卡片 class 含 card/company）。 */
    private String searchResultItemSelector = "[class*=company-card], [class*=ent-card], [class*=result-card], [class*=card] [class*=name]";

    /** 企业详情页关键信息行选择器（DOM 兜底，预留；实测详情为文本布局，已改文本解析）。 */
    private String detailRowSelector = "[class*=base-info] tr, [class*=base-info] li";

    /** 未登录/额度拦截特征文本（命中即视为未登录或额度受限，提示先登录）。 */
    private String loginRequiredText = "查询次数已达到上限";

    /** 详情页额度拦截特征文本（命中即视为当日额度用尽）。 */
    private String quotaExhaustedText = "今日查询额度已用完";

    // ===== 每账号容器隔离（复用 social-sdk-chrome）=====

    /** 每账号独立 Chrome 容器（true = 独立 profile/代理/指纹/端口，天然隔离）。 */
    private boolean perAccountContainer = true;

    /** 容器启动超时（秒）。 */
    private long containerLaunchTimeoutSeconds = 30;

    /** 最多同时常驻的账号容器数。 */
    private int maxActiveProfiles = 3;
}
