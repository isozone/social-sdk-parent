package cn.net.rjnetwork.xianyu.manager.account.controller;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.CookieRenewService;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chrome 内扫码登录（登录态人工恢复链路的 human-in-the-loop 交接点）。
 * <ol>
 *   <li>POST chrome-start：启动账号绑定的 Chrome 容器并导航到闲鱼登录页；</li>
 *   <li>用户用闲鱼 App 扫码并确认登录；</li>
 *   <li>GET chrome-status：前端轮询——经 CDP 从容器 cookie jar 提取 cookie，
 *       校验登录态后自动回写账号（ACTIVE），无需人工搬运。</li>
 * </ol>
 * 会话状态保存在本进程内存（单实例部署），账号维度一条。
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/login-renew")
public class RenewLoginController {

    private static final Logger log = LoggerFactory.getLogger(RenewLoginController.class);

    /** 闲鱼扫码登录页（登录成功后停留 goofish，cookie 全量落在容器 jar） */
    public static final String LOGIN_URL =
            "https://login.goofish.com/?redirectUrl=https%3A%2F%2Fwww.goofish.com%2F";

    private final AccountMapper accountMapper;
    private final AccountService accountService;
    private final CookieRenewService cookieRenewService;

    private final Map<Long, RenewSession> sessions = new ConcurrentHashMap<>();

    public RenewLoginController(AccountMapper accountMapper, AccountService accountService,
                                CookieRenewService cookieRenewService) {
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.cookieRenewService = cookieRenewService;
    }

    /** 会话（内存态）：记录开始时间，用于前端展示与超时提示 */
    public static class RenewSession {
        public final LocalDateTime startedAt = LocalDateTime.now();
    }

    /** 预检：账号存在性检查（Chrome 可执行文件检测在启动步骤内完成并返回明确错误）。 */
    @PostMapping("/chrome-check")
    public ApiResponse<Map<String, Object>> chromeCheck(@PathVariable Long accountId) {
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null) {
            return ApiResponse.ok(Map.of("ready", false, "message", "账号不存在"));
        }
        return ApiResponse.ok(Map.of("ready", true, "message", "环境就绪，可打开 Chrome 登录页"));
    }

    /** 启动 Chrome 容器并导航到闲鱼登录页。 */
    @PostMapping("/chrome-start")
    public ApiResponse<Map<String, Object>> chromeStart(@PathVariable Long accountId) {
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null) {
            return ApiResponse.fail("NOT_FOUND", "账号不存在");
        }
        boolean launched = accountService.launchChromeContainer(acc);
        if (!launched) {
            log.warn("[RENEW] account {} chrome container launch failed", accountId);
            return ApiResponse.fail("CHROME_FAIL", "Chrome 容器启动失败，请查看服务日志（常见原因：chrome.executable-path 未配置或缺少依赖库）");
        }
        boolean navigated = cookieRenewService.navigatePageTo(acc, LOGIN_URL);
        if (!navigated) {
            return ApiResponse.fail("NAV_FAIL", "打开登录页失败，请重试或查看服务日志");
        }
        sessions.put(accountId, new RenewSession());
        log.info("[RENEW] account {} chrome login session started", accountId);
        return ApiResponse.ok(Map.of(
                "sessionId", accountId,
                "status", "WAITING_SCAN",
                "message", "登录页已打开，请用闲鱼 App 扫码并确认登录"));
    }

    /** 轮询：提取容器 cookie → 校验登录态 → 自动回写账号。 */
    @GetMapping("/chrome-status")
    public ApiResponse<Map<String, Object>> chromeStatus(@PathVariable Long accountId) {
        if (sessions.get(accountId) == null) {
            return ApiResponse.ok(Map.of("status", "NOT_STARTED", "message", "请先点击「打开 Chrome 登录页」"));
        }
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null) {
            sessions.remove(accountId);
            return ApiResponse.ok(Map.of("status", "NOT_STARTED", "message", "账号不存在"));
        }
        Optional<String> cookie = cookieRenewService.extractCookieHeaderViaCdp(acc);
        if (cookie.isEmpty()) {
            return ApiResponse.ok(Map.of("status", "WAITING_SCAN",
                    "message", "尚未检测到登录 Cookie，请完成扫码并确认登录"));
        }
        boolean loggedIn;
        try {
            loggedIn = new XianyuLoginApiService(cookie.get()).checkLoginStatus(cookie.get()).loggedIn;
        } catch (Exception e) {
            log.warn("[RENEW] account {} checkLoginStatus error: {}", accountId, e.getMessage());
            loggedIn = false;
        }
        if (!loggedIn) {
            return ApiResponse.ok(Map.of("status", "WAITING_SCAN",
                    "message", "已取到 Cookie 但登录态未生效，请确认已在页面完成扫码并登录"));
        }
        // 自动回写：cookie + 状态恢复 + 清错误 + 刷新登录时间
        acc.setCookieHeader(cookie.get());
        acc.setStatus("ACTIVE");
        acc.setLastError(null);
        acc.setLastLoginAt(LocalDateTime.now());
        acc.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(acc);
        sessions.remove(accountId);
        log.info("[RENEW] account {} login cookie applied, account restored to ACTIVE", accountId);
        return ApiResponse.ok(Map.of("status", "SUCCESS",
                "message", "登录态已回写，账号恢复 ACTIVE，自动续期已就绪"));
    }
}
