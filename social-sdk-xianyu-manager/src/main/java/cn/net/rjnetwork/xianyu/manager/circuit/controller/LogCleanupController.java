package cn.net.rjnetwork.xianyu.manager.circuit.controller;

import cn.net.rjnetwork.xianyu.manager.circuit.service.LogCleanupService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志清理管理端 API。
 * <p>提供手动清理接口和日志统计查询。</p>
 */
@RestController
@RequestMapping("/api/log-cleanup")
public class LogCleanupController {

    private final LogCleanupService logCleanupService;

    public LogCleanupController(LogCleanupService logCleanupService) {
        this.logCleanupService = logCleanupService;
    }

    /**
     * 获取各日志表的统计信息。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(logCleanupService.getLogStats());
    }

    /**
     * 手动清理指定天数前的日志记录。
     *
     * @param keepDays 保留最近多少天（默认 7）
     */
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(
            @RequestParam(defaultValue = "7") int keepDays) {
        Map<String, Integer> result = logCleanupService.cleanupAll(keepDays);
        return ApiResponse.ok(result);
    }

    /**
     * 获取当前清理配置。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("enabled", logCleanupService.isEnabled());
        config.put("keepDays", logCleanupService.getKeepDays());
        return ApiResponse.ok(config);
    }
}
