package cn.net.rjnetwork.xianyu.manager.account.task;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.CookieRenewService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账号健康检查定时任务
 */
@Component
public class AccountHealthTask {

    private static final Logger logger = LoggerFactory.getLogger(AccountHealthTask.class);

    private final AccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CookieRenewService cookieRenewService;

    public AccountHealthTask(AccountMapper accountMapper, ApplicationEventPublisher eventPublisher,
                             CookieRenewService cookieRenewService) {
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
        this.cookieRenewService = cookieRenewService;
    }

    /**
     * 检测账号健康状态，由 ScheduledTasks 统一调度。
     */
    public void checkAccountHealth() {
        List<XianyuAccount> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<XianyuAccount>()
                        .in(XianyuAccount::getStatus, "ACTIVE", "OFFLINE", "FROZEN")
        );

        for (XianyuAccount account : accounts) {
            String prevStatus = account.getStatus();
            String cookie = account.getCookieHeader();
            // 未配置 Cookie：账号无法连接，视为离线
            if (cookie == null || cookie.isBlank()) {
                if (!"OFFLINE".equals(account.getStatus())) {
                    account.setStatus("OFFLINE");
                    account.setLastError("未配置 Cookie，无法连接");
                    accountMapper.updateById(account);
                    publishOffline(account);
                }
                continue;
            }

            try {
                // 通过 SDK 真实校验登录态，区分 Cookie 失效与连接失败
                XianyuLoginApiService loginApi = new XianyuLoginApiService(cookie);
                XianyuLoginApiService.LoginStatusResult r = loginApi.checkLoginStatus(cookie);

                if (r != null && r.loggedIn) {
                    // 健康：恢复在线（FROZEN 误冻自愈）
                    if ("FROZEN".equals(prevStatus)) {
                        logger.info("[HEALTH] 账号 {} 登录态仍有效，FROZEN 自愈为 ACTIVE（解除误冻）", account.getId());
                    }
                    account.setStatus("ACTIVE");
                    account.setLastLoginAt(LocalDateTime.now());
                    account.setLastError(null);
                    accountMapper.updateById(account);
                    continue;
                }

                // 校验未通过：严格区分「真正登录态失效」与「可恢复签名错误」
                // TOKEN_EXOIRED / ILLEGAL_REQUEST / TOKEN_EMPTY 只是 _m_h5_tk 签名 token 过期或预热抖动，
                // 登录 cookie 本体仍健康（闲鱼登录 cookie 可存活几十天），不能判为 COOKIE_EXPIRED，
                // 否则会把健康账号误标过期并反复告警（CookieRenewService/MessageService 均已按此白名单处理）。
                String msg = r == null ? "" : (r.message == null ? "" : r.message);
                String low = msg.toLowerCase();
                boolean recoverableSignError = low.contains("token_exoired")
                        || low.contains("illegal_request")
                        || low.contains("token_empty")
                        || low.contains("recoverable sign error");
                if (recoverableSignError) {
                    // 签名抖动：不改状态，等下一轮自然恢复（避免误报 COOKIE_EXPIRED）
                    continue;
                }
                // 真正登录态失效白名单：login-error / user_validate / session_expired / fail_sys_login
                boolean loginExpired = low.contains("login")
                        || low.contains("user_validate")
                        || low.contains("session_expired")
                        || low.contains("empty cookie");

                if (loginExpired) {
                    if (!"COOKIE_EXPIRED".equals(account.getStatus())) {
                        account.setStatus("COOKIE_EXPIRED");
                        account.setLastError(msg);
                        accountMapper.updateById(account);
                        publishCookieExpired(account);
                    }
                } else {
                    // 连接/网络失败 -> 离线
                    if (!"OFFLINE".equals(account.getStatus())) {
                        account.setStatus("OFFLINE");
                        account.setLastError(msg);
                        accountMapper.updateById(account);
                        publishOffline(account);
                    }
                }
            } catch (Exception e) {
                logger.error("Account health check failed for {}: {}", account.getAccountName(), e.getMessage());
                if (!"OFFLINE".equals(account.getStatus())) {
                    account.setStatus("OFFLINE");
                    account.setLastError(e.getMessage());
                    accountMapper.updateById(account);
                    publishOffline(account);
                }
            }
        }
    }

    private void publishCookieExpired(XianyuAccount account) {
        // 边沿触发：仅 ACTIVE→COOKIE_EXPIRED 翻转时发一次（库内已是过期态则静默），
        // 否则健康检查每 5 分钟发布一次 × 1h 冷却 = 每小时一封的邮件轰炸
        XianyuAccount dbAccount = accountMapper.selectById(account.getId());
        if (dbAccount != null && "COOKIE_EXPIRED".equals(dbAccount.getStatus())) {
            return;
        }
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getAccountName();
        // 与续期失败路径一致：掉线边沿自动把登录页打开待扫（用户点邮件直链即可扫码恢复）
        try {
            cookieRenewService.navigatePageTo(account,
                    cn.net.rjnetwork.xianyu.manager.account.controller.RenewLoginController.LOGIN_URL);
        } catch (Exception e) {
            logger.warn("auto-navigate to login page failed for account {}: {}", account.getId(), e.getMessage());
        }
        String controlUrl = "http://192.168.1.252:40000/captcha-control?accountId=" + account.getId();
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_COOKIE_EXPIRED", account.getId(), name,
                Map.of("accountName", name, "controlUrl", controlUrl)));
    }

    private void publishOffline(XianyuAccount account) {
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getAccountName();
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_OFFLINE", account.getId(), name,
                Map.of("accountName", name)));
    }
}
