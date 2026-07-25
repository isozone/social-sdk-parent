package cn.net.rjnetwork.xianyu.manager.task.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.task.model.ScheduledTask;
import cn.net.rjnetwork.xianyu.manager.task.service.ScheduledTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统一任务调度中心管理端 API —— B1。
 * <p>给前端「任务调度」页调用：分页列表、启停、改 cron、手动触发、查最近执行。
 * taskKey 对应 ScheduledTasks 的方法名（如 runCookieRefresh）。</p>
 */
@RestController
@RequestMapping("/api/scheduled-tasks")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /** 分页查询任务列表，可选 category/enabled 过滤。 */
    @GetMapping
    public ApiResponse<Page<ScheduledTask>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(scheduledTaskService.list(page, size, category, enabled));
    }

    /** 查单任务注册信息。 */
    @GetMapping("/{taskKey}")
    public ApiResponse<ScheduledTask> get(@PathVariable String taskKey) {
        return ApiResponse.ok(scheduledTaskService.get(taskKey));
    }

    /** 启停任务。 */
    @PutMapping("/{taskKey}/toggle")
    public ApiResponse<Boolean> toggle(@PathVariable String taskKey, @RequestParam boolean enabled) {
        return ApiResponse.ok(scheduledTaskService.toggle(taskKey, enabled));
    }

    /** 改任务 cron 表达式。改后需重启服务生效（静态注解链路）或由动态调度器即时生效。 */
    @PutMapping("/{taskKey}/cron")
    public ApiResponse<Boolean> updateCron(@PathVariable String taskKey, @RequestParam String cron) {
        return ApiResponse.ok(scheduledTaskService.updateCron(taskKey, cron));
    }

    /** 手动触发一次任务执行。返回执行结果摘要。 */
    @PostMapping("/{taskKey}/run")
    public ApiResponse<Map<String, Object>> triggerManually(@PathVariable String taskKey) {
        // 手动触发逻辑由 ScheduledTasks 提供 Runnable 注册表（避免本 Controller 反向调 ScheduledTasks 私有方法）
        // 这里先返回当前任务状态，前端可同时调对应任务的专用 run 接口（如 /api/account/cookie-renew/run）
        ScheduledTask row = scheduledTaskService.get(taskKey);
        if (row == null) return ApiResponse.fail("NOT_FOUND", "任务未注册");
        return ApiResponse.ok(Map.of(
                "taskKey", taskKey,
                "taskName", row.getTaskName() == null ? "" : row.getTaskName(),
                "lastRunAt", row.getLastRunAt() == null ? "" : row.getLastRunAt().toString(),
                "lastResult", row.getLastResult() == null ? "" : row.getLastResult(),
                "hint", "请调对应任务的专用 run 接口（如 /api/account/cookie-renew/run）执行"
        ));
    }
}
