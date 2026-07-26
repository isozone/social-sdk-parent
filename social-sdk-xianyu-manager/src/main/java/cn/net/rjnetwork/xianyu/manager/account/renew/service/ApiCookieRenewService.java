package cn.net.rjnetwork.xianyu.manager.account.renew.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cookie 接口续期服务 —— A2 轻量通道。
 * <p>与 A1（浏览器刷新）形成双通道：A2 优先（不启动 Chrome 容器，省资源），
 * 失败降级到 A1。链路：</p>
 * <ol>
 *   <li>用账号当前 cookie 构造 {@link XianyuMtopApiClient}，调一次轻量 MTOP 接口
 *       （mtop.alibaba.xianyu.user.userInfo.get），触发 {@link XianyuMtopApiClient#primeTokenIfNeeded}
 *       自动剔除过期 _m_h5_tk、让服务端重下发新 token；</li>
 *   <li>调 {@link XianyuMtopApiClient#getMergedCookie} 拿到合并 Set-Cookie 后的新 cookie；</li>
 *   <li>调 {@link XianyuLoginApiService#checkLoginStatus} 校验新 cookie 仍登录态；</li>
 *   <li>写回加密 cookie + 恢复 ACTIVE + 熔断记 success。</li>
 * </ol>
 *
 * <p>典型适用场景：_m_h5_tk token 过期（FAIL_SYS_TOKEN_EXOIRED）但 cookie2/unb 登录态仍健康——
 * 此时 A1 启浏览器是杀鸡用牛刀，A2 只需调一次 MTOP 让服务端重下发 token 即可。</p>
 */
@Service
public class ApiCookieRenewService {

    private static final Logger log = LoggerFactory.getLogger(ApiCookieRenewService.class);
    private static final String USER_INFO_API = "mtop.alibaba.xianyu.user.userInfo.get";
    private static final String USER_INFO_VERSION = "1.0";

    private final AccountMapper accountMapper;
    private final CircuitBreakerService circuitBreaker;
    private final cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;

    public ApiCookieRenewService(AccountMapper accountMapper,
                                 CircuitBreakerService circuitBreaker,
                                 cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory) {
        this.accountMapper = accountMapper;
        this.circuitBreaker = circuitBreaker;
        this.xianyuMtopClientFactory = xianyuMtopClientFactory;
    }

    /**
     * 对单账号执行 API 续期。
     * @return 续期结果：SUCCESS / FAILED / SKIPPED（熔断中）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RenewResult renewViaApi(XianyuAccount account) {
        if (!circuitBreaker.allowRequest(account.getId(), "API_COOKIE_RENEW")) {
            log.debug("[A2] account {} in circuit-break, skip", account.getId());
            return RenewResult.SKIPPED;
        }
        String cookie = account.getCookieHeader();
        if (cookie == null || cookie.isBlank()) {
            return RenewResult.FAILED;
        }

        try {
            // 1. 构造 MTOP client，调轻量接口触发 token 预热 + Set-Cookie 吸收
            XianyuMtopApiClient client = xianyuMtopClientFactory.create(account);
            JsonNode resp = client.callMtop(USER_INFO_API, USER_INFO_VERSION, "{}");
            // 拿到合并 Set-Cookie 后的新 cookie
            String newCookie = client.getMergedCookie();
            if (newCookie == null || newCookie.isBlank()) {
                circuitBreaker.recordFailure(account.getId(), "API_COOKIE_RENEW", "MTOP 响应无新 cookie");
                return RenewResult.FAILED;
            }

            // 2. 校验新 cookie 仍登录态
            XianyuLoginApiService.LoginStatusResult verify =
                    new XianyuLoginApiService(newCookie).checkLoginStatus(newCookie);
            if (verify == null || !verify.loggedIn) {
                circuitBreaker.recordFailure(account.getId(), "API_COOKIE_RENEW", "新 cookie 校验未通过");
                // A2 失败 → 调用方降级到 A1（浏览器刷新）
                return RenewResult.FAILED;
            }

            // 3. 写回 + 恢复 ACTIVE
            account.setCookieHeader(newCookie);
            // IM cookie 也可能被服务端更新（如新 x5sec），同步吸收
            if (account.getImCookieHeader() != null) {
                // merged cookie 已含 IM 部分，imCookieHeader 保持原值或由 captcha 链路单独刷新
            }
            account.setStatus("ACTIVE");
            account.setLastError(null);
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(account);
            circuitBreaker.recordSuccess(account.getId(), "API_COOKIE_RENEW");
            log.info("[A2] account {} cookie renewed via MTOP, resp code={}",
                    account.getId(), resp != null ? resp.path("ret").asText() : "null");
            return RenewResult.SUCCESS;
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            circuitBreaker.recordFailure(account.getId(), "API_COOKIE_RENEW", reason);
            log.warn("[A2] account {} API renew failed: {}", account.getId(), reason);
            return RenewResult.FAILED;
        }
    }

    /** 复用 A1 的 RenewResult 枚举，语义一致。 */
    public enum RenewResult { SUCCESS, SKIPPED, FAILED }
}
