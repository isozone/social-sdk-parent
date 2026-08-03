package cn.net.rjnetwork.riskbird.service;

import cn.net.rjnetwork.riskbird.api.ChromeRiskbirdDriver;
import cn.net.rjnetwork.riskbird.api.RiskbirdApiFacade;
import cn.net.rjnetwork.riskbird.api.RiskbirdPageDriver;
import cn.net.rjnetwork.riskbird.config.RiskbirdConfig;
import cn.net.rjnetwork.riskbird.model.RiskbirdCredentials;
import cn.net.rjnetwork.riskbird.model.RiskbirdLoginResult;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riskbird SDK 门面（多账户隔离）。
 *
 * <p>每个 accountId 对应一个独立账号会话（{@link RiskbirdAccount}）：
 * <ul>
 *   <li>默认模式：每账号独立 Chrome 容器（复用 social-sdk-chrome 的 {@link ChromeBrowser}，
 *       独立 profile/代理/指纹/端口，天然隔离）</li>
 *   <li>测试模式：可注入自定义 {@link RiskbirdDriverFactory}（如 mock 驱动）验证业务逻辑</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 * RiskbirdSdk sdk = new RiskbirdSdk(config, chromeBrowser);
 * RiskbirdSdk.RiskbirdAccount acc = sdk.account(1001L);
 * acc.api().loginWithCookie("cookie...");
 * RiskbirdSearchResult result = acc.api().search("某某科技", 1);
 * }</pre>
 *
 * <p>线程安全：账号 Map 并发安全，各账号 api 互不干扰。
 */
public class RiskbirdSdk {

    private static final Logger log = LoggerFactory.getLogger(RiskbirdSdk.class);

    /** 账号会话（隔离单位）。 */
    public static final class RiskbirdAccount {
        private final long accountId;
        private final RiskbirdApiFacade api;

        RiskbirdAccount(long accountId, RiskbirdApiFacade api) {
            this.accountId = accountId;
            this.api = api;
        }

        public long accountId() {
            return accountId;
        }

        public RiskbirdApiFacade api() {
            return api;
        }
    }

    /** 驱动工厂（测试注入 mock 用）。 */
    @FunctionalInterface
    public interface RiskbirdDriverFactory {
        RiskbirdPageDriver create(RiskbirdConfig config, long accountId);
    }

    private final RiskbirdConfig config;
    private final RiskbirdDriverFactory driverFactory;
    private final Map<Long, RiskbirdAccount> accounts = new ConcurrentHashMap<>();

    /**
     * 默认构造：每账号独立 Chrome 容器。
     *
     * @param chromeBrowser social-sdk-chrome 的浏览器门面（Spring 环境注入；非容器模式可传 null，
     *                      此时仅支持注入 mock driver 的测试场景，否则 driver 创建会失败）
     */
    public RiskbirdSdk(RiskbirdConfig config, ChromeBrowser chromeBrowser) {
        this(config, chromeBrowser != null
                ? (cfg, accountId) -> new ChromeRiskbirdDriver(cfg, chromeBrowser, accountId)
                : (cfg, accountId) -> {
                    throw new IllegalStateException(
                            "未提供 ChromeBrowser 且未注入 driver 工厂，无法创建页面驱动, accountId=" + accountId);
                });
    }

    /** 自定义驱动工厂构造（测试/扩展用）。 */
    public RiskbirdSdk(RiskbirdConfig config, RiskbirdDriverFactory driverFactory) {
        this.config = config;
        this.driverFactory = driverFactory;
    }

    /**
     * 获取账号会话（懒创建，重复调用返回同一实例）。
     */
    public RiskbirdAccount account(long accountId) {
        return accounts.computeIfAbsent(accountId, id -> {
            RiskbirdPageDriver driver = driverFactory.create(config, id);
            log.info("[RISKBIRD] 创建账号会话, accountId={}, driver={}", id, driver.getClass().getSimpleName());
            return new RiskbirdAccount(id, new RiskbirdApiFacade(config, driver));
        });
    }

    /**
     * 快捷：以账号密码登录并返回账号会话。
     */
    public RiskbirdAccount login(long accountId, String username, String password) throws Exception {
        RiskbirdAccount acc = account(accountId);
        RiskbirdLoginResult result = acc.api().loginWithPassword(username, password);
        if (!result.isSuccess()) {
            throw new IllegalStateException("登录失败, accountId=" + accountId + ", msg=" + result.getMessage());
        }
        return acc;
    }

    /**
     * 快捷：以 Cookie 登录并返回账号会话。
     */
    public RiskbirdAccount login(long accountId, String cookieHeader) throws Exception {
        RiskbirdAccount acc = account(accountId);
        RiskbirdLoginResult result = acc.api().loginWithCookie(cookieHeader);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Cookie 登录失败, accountId=" + accountId + ", msg=" + result.getMessage());
        }
        return acc;
    }

    /**
     * 快捷：以凭证对象登录并返回账号会话（账号密码优先，其次 Cookie）。
     */
    public RiskbirdAccount login(long accountId, RiskbirdCredentials credentials) throws Exception {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials 不能为 null");
        }
        if (credentials.getCookieHeader() != null && !credentials.getCookieHeader().isBlank()) {
            return login(accountId, credentials.getCookieHeader());
        }
        return login(accountId, credentials.getUsername(), credentials.getPassword());
    }

    /** 关闭指定账号会话。 */
    public void closeAccount(long accountId) {
        RiskbirdAccount acc = accounts.remove(accountId);
        if (acc != null) {
            acc.api().close();
            log.info("[RISKBIRD] 关闭账号会话, accountId={}", accountId);
        }
    }

    /** 关闭全部账号会话。 */
    public void close() {
        for (Long id : accounts.keySet()) {
            closeAccount(id);
        }
        accounts.clear();
    }

    /** 当前账号会话数。 */
    public int accountCount() {
        return accounts.size();
    }
}
