package cn.net.rjnetwork.xianyu.manager.account.task;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Session Keepalive 循环 —— BOT-A1。
 *
 * <p>每账号周期调用 mtop.taobao.idlemessage.pc.loginuser.get，保持会话活着，
 * 避免长时间无业务时闲鱼侧主动断开会话。</p>
 *
 * <p>与 A4 Token 续期互补：A4 刷新 token，本任务轻量探测会话是否仍活着。</p>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫启用账号（status != DISABLED）；</li>
 *   <li>调 facade.getLoginUserInfo()；</li>
 *   <li>成功：更新 last_keepalive_at（无业务字段写回，避免脏写）；</li>
 *   <li>失败：记 warn 日志，不触发风控暂停（由 BOT-A6 处理）。</li>
 * </ol>
 */
@Component
public class SessionKeepaliveTask {

    private static final Logger log = LoggerFactory.getLogger(SessionKeepaliveTask.class);

    private final AccountMapper accountMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;

    public SessionKeepaliveTask(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** 每 10 分钟探测一次所有账号会话存活。 */
    @Scheduled(cron = "0 */10 * * * *")
    public void runKeepalive() {
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<XianyuAccount>()
                .ne(XianyuAccount::getStatus, "DISABLED"));
        int ok = 0, fail = 0;
        for (XianyuAccount acc : accounts) {
            try {
                if (acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
                    fail++;
                    continue;
                }
                XianyuMtopApiClient mtopClient = xianyuMtopClientFactory.create(acc);
                XianyuProfileApiService profileApi = new XianyuProfileApiService(mtopClient);
                JsonNode resp = profileApi.getLoginUserInfo();
                String ret = resp != null ? resp.path("ret").toString() : "";
                if (ret.contains("FAIL")) {
                    fail++;
                    log.warn("[BOT-A1] account {} keepalive 失败: ret={}", acc.getId(), ret);
                } else {
                    ok++;
                }
            } catch (Exception e) {
                fail++;
                log.warn("[BOT-A1] account {} keepalive 异常: {}", acc.getId(), e.getMessage());
            }
        }
        log.info("[BOT-A1] session keepalive 完成: ok={}, fail={}, total={}", ok, fail, accounts.size());
    }
}
