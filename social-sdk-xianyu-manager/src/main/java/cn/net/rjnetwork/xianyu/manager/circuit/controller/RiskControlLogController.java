package cn.net.rjnetwork.xianyu.manager.circuit.controller;

import cn.net.rjnetwork.xianyu.manager.circuit.model.RiskControlLog;
import cn.net.rjnetwork.xianyu.manager.circuit.service.RiskControlLogService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 风控冷却/限流日志管理端 API —— A5。
 * <p>给前端「风控日志」页查询诊断 + 手动标已恢复用。
 * 由 CircuitBreakerService.recordFailure 在熔断器 OPEN 时自动写日志。</p>
 */
@RestController
@RequestMapping("/api/risk-control")
public class RiskControlLogController {

    private final RiskControlLogService riskControlLogService;

    public RiskControlLogController(RiskControlLogService riskControlLogService) {
        this.riskControlLogService = riskControlLogService;
    }

    /** 分页查询风控日志，可选 accountId/triggerType/recovered 过滤。 */
    @GetMapping("/logs")
    public ApiResponse<Page<RiskControlLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Integer recovered) {
        return ApiResponse.ok(riskControlLogService.list(page, size, accountId, triggerType, recovered));
    }

    /** 手动触发「冷却到期标记已恢复」扫描，返回本次标记条数。 */
    @PostMapping("/mark-recovered")
    public ApiResponse<Integer> markRecovered() {
        return ApiResponse.ok(riskControlLogService.markExpiredAsRecovered());
    }
}
