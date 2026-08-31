package cn.net.rjnetwork.xianyu.manager.account.task;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Session Keepalive 循环 —— BOT-A1。
 *
 * <p>每账号周期调用 mtop.taobao.idlemessage.pc.loginuser.get，保持会话活着，
 * 避免长时间无业务时闲鱼侧主动断开会话。</p>
 *
 * <p>与 A4 Token 续期互补：A4 刷新 token，本任务轻量探测会话是否仍活着。</p>
 *
 * <p>为避免高频且规律的探活被闲鱼风控判定为机器人脚本，本任务做了三层抑制：</p>
 * <ol>
 *   <li><b>长周期</b>：由 ScheduledTasks 统一调度（cron 每 60 分钟一次），频率与
 *       token 续期（110 分钟）形成阶梯，探活不再比续期更频繁；</li>
 *   <li><b>按需跳过</b>：距上次 keepalive 不足 {@link #MIN_INTERVAL_MINUTES} 的账号直接跳过，
 *       不重复探测；</li>
 *   <li><b>错峰 + 随机抖动</b>：账号按 id 散列到不同时间片，并在执行前随机延迟 0~{@link #MAX_JITTER_SECONDS}
 *       秒，打散整点齐发的规律性特征。</li>
 * </ol>
 *
 * <p>链路：</p>
 * <ol>
 *   <li>扫启用账号（status != DISABLED）；</li>
 *   <li>对到期账号调 facade.getLoginUserInfo()；</li>
 *   <li>成功：写回 last_keepalive_at（记录最近探测时间，供下次按需判断）；</li>
 *   <li>失败：记 warn 日志，不触发风控暂停（由 BOT-A6 处理）。</li>
 * </ol>
 */
@Component
public class SessionKeepaliveTask {

    private static final Logger log = LoggerFactory.getLogger(SessionKeepaliveTask.class);

    /** 最小探活间隔（分钟）：距上次成功探活不足此值则按需跳过。 */
    private static final int MIN_INTERVAL_MINUTES = 60;

    /** 错峰时间片数：账号按 id 散列到 N 个片，同一次执行只会挨个探测而非整点齐发。 */
    private static final int STAGGER_BUCKETS = 6;

    /** 单账号随机抖动上限（秒），打散规律性。 */
    private static final int MAX_JITTER_SECONDS = 30;

    private final AccountMapper accountMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.net.rjnetwork.xianyu.manager.sdk.XianyuMtopClientFactory xianyuMtopClientFactory;

    public SessionKeepaliveTask(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 执行一次 Session Keepalive 批次 —— 由 ScheduledTasks 统一调度（cron 每 60 分钟一次），
     * 管理端也可手动触发。移除独立 @Scheduled 防止与 ScheduledTasks 双调度。
     */
    public void runScheduled() {
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<XianyuAccount>()
                .ne(XianyuAccount::getStatus, "DISABLED"));
        int ok = 0, fail = 0, skipped = 0;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MIN_INTERVAL_MINUTES);
        for (XianyuAccount acc : accounts) {
            // 按需跳过：距上次成功探活不足阈值则不重复探测
            if (acc.getLastKeepaliveAt() != null && acc.getLastKeepaliveAt().isAfter(cutoff)) {
                skipped++;
                continue;
            }
            // 错峰：账号按 id 散列到时间片，且随机抖动，打散整点齐发的规律性
            long bucket = acc.getId() != null ? acc.getId() % STAGGER_BUCKETS : 0;
            double phase = bucket * (1.0 / STAGGER_BUCKETS) + ThreadLocalRandom.current().nextDouble(0, 0.15);
            sleepQuietly((long) (phase * MAX_JITTER_SECONDS * 1000));
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
                    acc.setLastKeepaliveAt(LocalDateTime.now());
                    accountMapper.updateById(acc);
                }
            } catch (Exception e) {
                fail++;
                log.warn("[BOT-A1] account {} keepalive 异常: {}", acc.getId(), e.getMessage());
            }
        }
        log.info("[BOT-A1] session keepalive 完成: ok={}, fail={}, skipped={}, total={}",
                ok, fail, skipped, accounts.size());
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
