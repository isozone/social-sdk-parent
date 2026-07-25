package cn.net.rjnetwork.xianyu.manager.circuit.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 风控冷却/限流日志 —— A5。
 * <p>每次账号触发风控（滑块 punish / FAIL_SYS_USER_VALIDATE / 频率限流）写一行，
 * 记录触发原因、冷却截止时间、关联批次/接口。便于管理端「风控日志」页检索诊断。</p>
 *
 * <p>与 CircuitBreakerService 的关系：熔断器只存内存态（OPEN/HALF_OPEN/CLOSED），
 * 本表是持久化审计日志 —— 启动时熔断器可从最近未冷却的本表记录恢复状态。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_control_log")
public class RiskControlLog extends BaseEntity {

    /** 账号 ID */
    private Long accountId;
    /** 触发类型：CAPTCHA / RATE_LIMIT / TOKEN_EXPIRED / LOGIN_FAILED / USER_VALIDATE */
    private String triggerType;
    /** 触发场景：COOKIE_RENEW / TOKEN_RENEWAL / LOGIN_RENEW / MESSAGE_SEND / MTOP_CALL */
    private String triggerScene;
    /** 风控原始标识，如 RGV587_ERROR::SM / FAIL_SYS_USER_VALIDATE / FAIL_SYS_RATE_LIMIT */
    private String riskCode;
    /** 失败原因简述 */
    private String failureReason;
    /** 冷却时长（秒） */
    private Integer cooldownSeconds;
    /** 冷却截止时间 */
    private LocalDateTime cooldownUntil;
    /** 关联的 batch_job.id，便于查批次明细；可空 */
    private Long batchJobId;
    /** 触发时间 */
    private LocalDateTime triggeredAt;
    /** 是否已恢复（冷却到期后由定时任务标 1） */
    private Integer recovered;
}
