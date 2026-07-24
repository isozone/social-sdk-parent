package cn.net.rjnetwork.xianyu.manager.batch.controller;

import cn.net.rjnetwork.xianyu.manager.batch.model.BatchJob;
import cn.net.rjnetwork.xianyu.manager.batch.model.BatchJobItem;
import cn.net.rjnetwork.xianyu.manager.batch.service.BatchJobService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 批次日志管理端 API —— B9 基础设施给前端日志中心统一调用。
 * <p>不分任务种类，前端只需 jobType 过滤即可渲染不同日志页（补发/续期/评价/擦亮等），
 * 详情页用 batchId 拉明细列表。summaryByJobType 给日志中心总览卡片用。</p>
 */
@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

    private final BatchJobService batchJobService;

    public BatchJobController(BatchJobService batchJobService) {
        this.batchJobService = batchJobService;
    }

    /** 分页查询批次列表，可选 jobType/status 过滤。 */
    @GetMapping
    public ApiResponse<Page<BatchJob>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(batchJobService.listBatches(page, size, jobType, status));
    }

    /** 查单批次的明细列表（按时间升序，与执行顺序一致）。 */
    @GetMapping("/{batchId}/items")
    public ApiResponse<List<BatchJobItem>> items(@PathVariable Long batchId) {
        return ApiResponse.ok(batchJobService.listItems(batchId));
    }

    /** 最近 7 天批次摘要统计（按 jobType 聚合），给日志中心总览卡片用。 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(batchJobService.summaryByJobType());
    }
}
