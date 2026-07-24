package cn.net.rjnetwork.xianyu.manager.account.renew.controller;

import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.CookieRefreshScheduleMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledCookiesRefreshLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.CookieRefreshSchedule;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledCookiesRefreshLog;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.ApiCookieRenewService;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.CookieRenewService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * Cookie 浏览器刷新管理端 API —— A1。
 * <p>给前端「Cookie 刷新日志」页 + 账号页「刷新计划配置」弹窗调用。
 * 手动触发批次走 POST /run，调 CookieRenewService.runBatch(MANUAL, true)。</p>
 */
@RestController
@RequestMapping("/api/account/cookie-renew")
public class CookieRenewController {

    private final CookieRenewService cookieRenewService;
    private final ApiCookieRenewService apiCookieRenewService;
    private final CookieRefreshScheduleMapper scheduleMapper;
    private final ScheduledCookiesRefreshLogMapper logMapper;
    private final AccountMapper accountMapper;

    public CookieRenewController(CookieRenewService cookieRenewService,
                                 ApiCookieRenewService apiCookieRenewService,
                                 CookieRefreshScheduleMapper scheduleMapper,
                                 ScheduledCookiesRefreshLogMapper logMapper,
                                 AccountMapper accountMapper) {
        this.cookieRenewService = cookieRenewService;
        this.apiCookieRenewService = apiCookieRenewService;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
        this.accountMapper = accountMapper;
    }

    /** 创建/更新账号刷新计划。 */
    @PostMapping("/schedule")
    public ApiResponse<CookieRefreshSchedule> upsertSchedule(
            @RequestParam Long accountId,
            @RequestParam(required = false) Integer intervalMinutes,
            @RequestParam(required = false) Integer onlyOnExpired) {
        return ApiResponse.ok(cookieRenewService.upsertSchedule(accountId, intervalMinutes, onlyOnExpired));
    }

    /** 启停账号刷新计划。 */
    @PutMapping("/schedule/{accountId}")
    public ApiResponse<Boolean> toggleSchedule(
            @PathVariable Long accountId, @RequestParam boolean enabled) {
        return ApiResponse.ok(cookieRenewService.toggleSchedule(accountId, enabled));
    }

    /** 查询某账号的刷新计划；不存在返回 null。 */
    @GetMapping("/schedule/{accountId}")
    public ApiResponse<CookieRefreshSchedule> getSchedule(@PathVariable Long accountId) {
        return ApiResponse.ok(scheduleMapper.selectByAccountId(accountId));
    }

    /** 手动触发一次刷新批次（仅刷新失效账号）。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(cookieRenewService.runBatch("MANUAL", true));
    }

    /**
     * A2 轻量通道：对单账号执行 MTOP 接口续期。
     * <p>典型场景：_m_h5_tk token 过期但 cookie2/unb 登录态仍健康，调一次 MTOP 即可续 token，
     * 无需启动 Chrome 容器。返回 SUCCESS/FAILED/SKIPPED（熔断中）。</p>
     */
    @PostMapping("/api-renew/{accountId}")
    public ApiResponse<String> renewViaApi(@PathVariable Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) return ApiResponse.fail("NOT_FOUND", "账号不存在");
        ApiCookieRenewService.RenewResult r = apiCookieRenewService.renewViaApi(account);
        return ApiResponse.ok(r.name());
    }

    /** 分页查询批次日志，给前端「Cookie 刷新日志」页用。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledCookiesRefreshLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledCookiesRefreshLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledCookiesRefreshLog>().orderByDesc(ScheduledCookiesRefreshLog::getStartedAt)));
    }
}
