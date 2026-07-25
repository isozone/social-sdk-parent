package cn.net.rjnetwork.xianyu.manager.product.polish.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.polish.mapper.ScheduledPolishLogMapper;
import cn.net.rjnetwork.xianyu.manager.product.polish.model.ScheduledPolishLog;
import cn.net.rjnetwork.xianyu.manager.product.polish.service.ScheduledPolishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 定时擦亮管理端 API —— B4。
 * <p>给前端「擦亮日志」页调用：手动触发批次 + 分页查询日志。
 * 擦亮走商品编辑链路触发排序刷新（闲鱼侧 SDK 无专门 polish API）。</p>
 */
@RestController
@RequestMapping("/api/polish")
public class PolishController {

    private final ScheduledPolishService polishService;
    private final ScheduledPolishLogMapper logMapper;

    public PolishController(ScheduledPolishService polishService, ScheduledPolishLogMapper logMapper) {
        this.polishService = polishService;
        this.logMapper = logMapper;
    }

    /** 手动触发一次擦亮批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(polishService.runBatch("MANUAL"));
    }

    /** 分页查询擦亮批次日志。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledPolishLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledPolishLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledPolishLog>().orderByDesc(ScheduledPolishLog::getStartedAt)));
    }
}
