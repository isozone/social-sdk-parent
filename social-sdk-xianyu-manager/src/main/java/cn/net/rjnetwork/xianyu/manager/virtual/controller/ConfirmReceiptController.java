package cn.net.rjnetwork.xianyu.manager.virtual.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipConfigMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
import cn.net.rjnetwork.xianyu.manager.virtual.service.ConfirmReceiptService;
import org.springframework.web.bind.annotation.*;

/**
 * 自动确认收货管理端 API —— A10。
 * <p>给前端「确认收货话术配置」+ 手动触发批次用。
 * 模板存于 VirtualShipConfig.confirmReceiptMessage，null 走默认话术。</p>
 */
@RestController
@RequestMapping("/api/confirm-receipt")
public class ConfirmReceiptController {

    private final ConfirmReceiptService confirmReceiptService;
    private final VirtualShipConfigMapper configMapper;

    public ConfirmReceiptController(ConfirmReceiptService confirmReceiptService,
                                    VirtualShipConfigMapper configMapper) {
        this.confirmReceiptService = confirmReceiptService;
        this.configMapper = configMapper;
    }

    /** 手动触发一次催确认收货批次。 */
    @PostMapping("/run")
    public ApiResponse<Long> runManually() {
        return ApiResponse.ok(confirmReceiptService.runBatch("MANUAL"));
    }

    /** 设置账号确认收货话术模板；message 为空则清回默认。 */
    @PutMapping("/template/{accountId}")
    public ApiResponse<VirtualShipConfig> setTemplate(
            @PathVariable Long accountId, @RequestParam(required = false) String message) {
        VirtualShipConfig config = configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VirtualShipConfig>()
                .eq(VirtualShipConfig::getAccountId, accountId).last("LIMIT 1"));
        if (config == null) {
            return ApiResponse.fail("NOT_FOUND", "账号发货配置不存在，请先在发货配置页创建");
        }
        config.setConfirmReceiptMessage(message);
        configMapper.updateById(config);
        return ApiResponse.ok(config);
    }
}
