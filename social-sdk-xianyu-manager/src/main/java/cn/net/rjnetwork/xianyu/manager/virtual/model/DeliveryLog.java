package cn.net.rjnetwork.xianyu.manager.virtual.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 自动发货落库审计 —— A8。
 * <p>每次自动发货主链路跑完写一行：订单/商品/卡券/规则评估结果/发送结果/耗时，
 * 便于管理端「发货日志」页检索诊断 + A9 补发任务据此重试。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("delivery_log")
public class DeliveryLog extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 订单 ID（关联 xianyu_order.id） */
    private Long orderId;
    /** 商品 ID */
    private Long productId;
    /** 买家 ID（闲鱼侧） */
    private String buyerId;
    /** 命中的卡券 ID（关联 ship_card.id）；可空（LINK/FILE 类型无卡券） */
    private Long shipCardId;
    /** 规则评估结果：PASS / BLOCKED / NOTIFY_ONLY / DELAY */
    private String ruleDecision;
    /** 命中的规则名（ruleDecision=BLOCKED 时填） */
    private String hitRuleName;
    /** 发货内容（脱敏后存，便于审计） */
    private String deliverContent;
    /** 发货状态：SUCCESS / FAILED / SKIPPED / DELAYED */
    private String status;
    /** 失败/跳过原因 */
    private String failureReason;
    /** 关联的 batch_job.id；可空 */
    private Long batchJobId;
    /** 发货时间 */
    private LocalDateTime shippedAt;
    /** 该次发货耗时（毫秒） */
    private Long durationMs;
}
