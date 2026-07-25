package cn.net.rjnetwork.xianyu.manager.order.rate.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.AutoRateConfigMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRateLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.AutoRateConfig;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.ScheduledRateLog;
import cn.net.rjnetwork.xianyu.manager.order.rate.service.AutoRateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 自动评价管理端 API —— B2。
 * <p>给前端「自动评价配置」+「评价日志」页调用。
 * 配置走 auto_rate_config（每账号一条，accountId=null 为全局规则），
 * 话术模板支持 {itemTitle}/{buyerNick}/{accountName} 占位符。</p>
 */
@RestController
@RequestMapping("/api/auto-rate")
public class AutoRateController {

    private final AutoRateService autoRateService;
    private final AutoRateConfigMapper configMapper;
    private final ScheduledRateLogMapper logMapper;

    public AutoRateController(AutoRateService autoRateService,
                              AutoRateConfigMapper configMapper,
                              ScheduledRateLogMapper logMapper) {
        this.autoRateService = autoRateService;
        this.configMapper = configMapper;
        this.logMapper = logMapper;
    }

    /** 新建/更新账号自动评价配置。 */
    @PostMapping("/config")
    public ApiResponse<AutoRateConfig> upsertConfig(@RequestBody AutoRateConfig config) {
        if (config.getEnabled() == null) config.setEnabled(1);
        if (config.getRateLevel() == null) config.setRateLevel("GOOD");
        if (config.getDelayDays() == null) config.setDelayDays(1);
        if (config.getId() == null) configMapper.insert(config);
        else configMapper.updateById(config);
        return ApiResponse.ok(config);
    }

    /** 查账号生效配置（账号专属优先，回退全局）。 */
    @GetMapping("/config/{accountId}")
    public ApiResponse<AutoRateConfig> getConfig(@PathVariable Long accountId) {
        return ApiResponse.ok(configMapper.selectEffectiveForAccount(accountId));
    }

    /** 启停账号配置。 */
    @PutMapping("/config/{id}/toggle")
    public ApiResponse<Boolean> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        AutoRateConfig c = configMapper.selectById(id);
        if (c == null) return ApiResponse.fail("NOT_FOUND", "配置不存在");
        c.setEnabled(enabled ? 1 : 0);
        configMapper.updateById(c);
        return ApiResponse.ok(true);
    }

    /** 手动触发一次自动评价批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(autoRateService.runBatch("MANUAL"));
    }

    /** 分页查询评价批次日志。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledRateLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledRateLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledRateLog>().orderByDesc(ScheduledRateLog::getStartedAt)));
    }
}
