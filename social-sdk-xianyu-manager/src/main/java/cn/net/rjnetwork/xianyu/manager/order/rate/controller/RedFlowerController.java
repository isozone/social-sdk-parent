package cn.net.rjnetwork.xianyu.manager.order.rate.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.RedFlowerConfigMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRedFlowerLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.RedFlowerConfig;
import cn.net.rjnetwork.xianyu.manager.order.rate.model.ScheduledRedFlowerLog;
import cn.net.rjnetwork.xianyu.manager.order.rate.service.RedFlowerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 求小红花管理端 API —— B3。
 * <p>给前端「红花配置」+「红花日志」页调用。
 * 配置走 red_flower_config（每账号一条，accountId=null 为全局规则），
 * 今日送花计数 todaySentCount 由定时任务每日 reset。</p>
 */
@RestController
@RequestMapping("/api/red-flower")
public class RedFlowerController {

    private final RedFlowerService redFlowerService;
    private final RedFlowerConfigMapper configMapper;
    private final ScheduledRedFlowerLogMapper logMapper;

    public RedFlowerController(RedFlowerService redFlowerService,
                               RedFlowerConfigMapper configMapper,
                               ScheduledRedFlowerLogMapper logMapper) {
        this.redFlowerService = redFlowerService;
        this.configMapper = configMapper;
        this.logMapper = logMapper;
    }

    /** 新建/更新账号红花配置。 */
    @PostMapping("/config")
    public ApiResponse<RedFlowerConfig> upsertConfig(@RequestBody RedFlowerConfig config) {
        if (config.getEnabled() == null) config.setEnabled(1);
        if (config.getTargetType() == null) config.setTargetType("buyer");
        if (config.getDailyLimit() == null) config.setDailyLimit(20);
        if (config.getTodaySentCount() == null) config.setTodaySentCount(0);
        if (config.getId() == null) configMapper.insert(config);
        else configMapper.updateById(config);
        return ApiResponse.ok(config);
    }

    /** 查账号生效配置。 */
    @GetMapping("/config/{accountId}")
    public ApiResponse<RedFlowerConfig> getConfig(@PathVariable Long accountId) {
        return ApiResponse.ok(configMapper.selectEffectiveForAccount(accountId));
    }

    /** 启停账号配置。 */
    @PutMapping("/config/{id}/toggle")
    public ApiResponse<Boolean> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        RedFlowerConfig c = configMapper.selectById(id);
        if (c == null) return ApiResponse.fail("NOT_FOUND", "配置不存在");
        c.setEnabled(enabled ? 1 : 0);
        configMapper.updateById(c);
        return ApiResponse.ok(true);
    }

    /** 手动触发一次求红花批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(redFlowerService.runBatch("MANUAL"));
    }

    /** 分页查询红花批次日志。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledRedFlowerLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledRedFlowerLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledRedFlowerLog>().orderByDesc(ScheduledRedFlowerLog::getStartedAt)));
    }
}
