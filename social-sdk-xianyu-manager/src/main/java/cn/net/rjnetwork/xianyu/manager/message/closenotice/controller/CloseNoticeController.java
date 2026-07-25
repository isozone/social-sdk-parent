package cn.net.rjnetwork.xianyu.manager.message.closenotice.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.mapper.ScheduledCloseNoticeLogMapper;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.model.ScheduledCloseNoticeLog;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.service.CloseNoticeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 定时关闭平台通知管理端 API —— B5。
 * <p>给前端「关闭通知日志」页调用：手动触发批次 + 分页查询日志。</p>
 */
@RestController
@RequestMapping("/api/close-notice")
public class CloseNoticeController {

    private final CloseNoticeService closeNoticeService;
    private final ScheduledCloseNoticeLogMapper logMapper;

    public CloseNoticeController(CloseNoticeService closeNoticeService,
                                 ScheduledCloseNoticeLogMapper logMapper) {
        this.closeNoticeService = closeNoticeService;
        this.logMapper = logMapper;
    }

    /** 手动触发一次关闭通知批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(closeNoticeService.runBatch("MANUAL"));
    }

    /** 分页查询关闭通知批次日志。 */
    @GetMapping("/logs")
    public ApiResponse<Page<ScheduledCloseNoticeLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ScheduledCloseNoticeLog> p = new Page<>(page, size);
        return ApiResponse.ok(logMapper.selectPage(p,
                new LambdaQueryWrapper<ScheduledCloseNoticeLog>().orderByDesc(ScheduledCloseNoticeLog::getStartedAt)));
    }
}
