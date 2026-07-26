package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 手动触发虚拟发货 / 发卡请求
 */
@Data
public class VirtualShipSendCardRequest {
    /** 优先：直接触发已有任务 */
    private Long taskId;
    /** 兼容：按订单创建/触发发货任务 */
    private Long orderId;
}
