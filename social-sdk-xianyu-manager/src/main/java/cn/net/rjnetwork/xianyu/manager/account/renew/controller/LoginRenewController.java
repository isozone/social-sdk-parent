package cn.net.rjnetwork.xianyu.manager.account.renew.controller;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.LoginRenewScheduleMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledLoginRenewLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.LoginRenewSchedule;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledLoginRenewLog;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.LoginRenewService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 登录续期管理端 API —— A3。
 * <p>给前端「登录续期日志」页 + 账号页「登录续期计划配置」弹窗调用。
 * 手动触发批次走 POST /run，调 LoginRenewService.runBatch(MANUAL)。</p>
 */
@RestController
@RequestMapping("/api/account/login-renew")
public class LoginRenewController {

    private final LoginRenewService loginRenewService;
    private final LoginRenewScheduleMapper scheduleMapper;
    private final ScheduledLoginRenewLogMapper logMapper;
    private final AccountMapper accountMapper;

    public LoginRenewController(LoginRenewService loginRenewService,
                                 LoginRenewScheduleMapper scheduleMapper,
                                 ScheduledLoginRenewLogMapper logMapper,
                                 AccountMapper accountMapper) {
        this.loginRenewService = loginRenewService;
        this.scheduleMapper = scheduleMapper;
        this.logMapper = logMapper;
        this.accountMapper = accountMapper;
    }

    /** 创建/更新账号登录续期计划。 */
    @PostMapping("/schedule")
    public ApiResponse<LoginRenewSchedule> upsertSchedule(
            @RequestParam Long accountId,
            @RequestParam(required = false) String loginMethod,
            @RequestParam(required = false) String passwordEncrypted,
            @RequestParam(required = false) Integer maxRetry) {
        return ApiResponse.ok(loginRenewService.upsertSchedule(accountId, loginMethod, passwordEncrypted, maxRetry));
    }

    /** 启停账号登录续期计划。 */
    @PutMapping("/schedule/{accountId}")
    public ApiResponse<Boolean> toggleSchedule(
            @PathVariable Long accountId, @RequestParam boolean enabled) {
        return ApiResponse.ok(loginRenewService.toggleSchedule(accountId, enabled));
    }

    /** 查询某账号的登录续期计划；不存在返回 null。 */
    @GetMapping("/schedule/{accountId}")
    public ApiResponse<LoginRenewSchedule> getSchedule(@PathVariable Long accountId) {
        return ApiResponse.ok(scheduleMapper.selectByAccountId(accountId));
    }

    /** 手动触发一次登录续期批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(loginRenewService.runBatch("MANUAL"));
    }

    /** 分页查询批次日志，给前端「登录续期日志」页用。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledLoginRenewLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledLoginRenewLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledLoginRenewLog>().orderByDesc(ScheduledLoginRenewLog::getStartedAt)));
    }
}
