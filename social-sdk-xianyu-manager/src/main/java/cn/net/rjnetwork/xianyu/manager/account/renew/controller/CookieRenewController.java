package cn.net.rjnetwork.xianyu.manager.account.renew.controller;

import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.CookieRefreshScheduleMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledCookiesRefreshLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.CookieRefreshSchedule;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledCookiesRefreshLog;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.CookieRenewService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
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
    private final CookieRefreshScheduleMapper scheduleMapper;
    private final ScheduledCookiesRefreshLogMapper logMapper;

    public CookieRenewController(CookieRenewService cookieRenewService,
                                 CookieRefreshScheduleMapper scheduleMapper,
                                 ScheduledCookiesRefreshLogMapper logMapper) {
        this.cookieRenewService = cookieRenewService;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
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
