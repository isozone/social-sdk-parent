package cn.net.rjnetwork.xianyu.manager.account.renew.controller;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ImTokenCacheMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledTokenRenewalLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ImTokenCache;
import cn.net.rjnetwork.xianyu.manager.account.renew.model.ScheduledTokenRenewalLog;
import cn.net.rjnetwork.xianyu.manager.account.renew.service.TokenRenewalService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * Token/IM 续期管理端 API —— A4。
 * <p>给前端「Token 续期日志」页 + 账号页「token 缓存状态」查询用。
 * 手动触发批次走 POST /run，调 TokenRenewalService.runBatch(MANUAL)。</p>
 */
@RestController
@RequestMapping("/api/account/token-renewal")
public class TokenRenewalController {

    private final TokenRenewalService tokenRenewalService;
    private final ImTokenCacheMapper tokenCacheMapper;
    private final ScheduledTokenRenewalLogMapper logMapper;
    private final AccountMapper accountMapper;

    public TokenRenewalController(TokenRenewalService tokenRenewalService,
                                   ImTokenCacheMapper tokenCacheMapper,
                                   ScheduledTokenRenewalLogMapper logMapper,
                                   AccountMapper accountMapper) {
        this.tokenRenewalService = tokenRenewalService;
        this.tokenCacheMapper = tokenCacheMapper;
        this.logMapper = logMapper;
        this.accountMapper = accountMapper;
    }

    /** 查询某账号的 token 缓存状态；不存在返回 null。 */
    @GetMapping("/cache/{accountId}")
    public ApiResponse<ImTokenCache> getCache(@PathVariable Long accountId) {
        return ApiResponse.ok(tokenCacheMapper.selectByAccountId(accountId));
    }

    /** 手动触发一次 Token/IM 续期批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(tokenRenewalService.runBatch("MANUAL"));
    }

    /** 分页查询批次日志，给前端「Token 续期日志」页用。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledTokenRenewalLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledTokenRenewalLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledTokenRenewalLog>().orderByDesc(ScheduledTokenRenewalLog::getStartedAt)));
    }
}
