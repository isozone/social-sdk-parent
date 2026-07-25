package cn.net.rjnetwork.xianyu.manager.circuit.service;

import cn.net.rjnetwork.xianyu.manager.circuit.mapper.RiskControlLogMapper;
import cn.net.rjnetwork.xianyu.manager.circuit.model.RiskControlLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 风控冷却/限流日志服务 —— A5。
 * <p>每次账号触发风控（滑块 punish / FAIL_SYS_USER_VALIDATE / 频率限流）写一行，
 * 由 {@link cn.net.rjnetwork.xianyu.manager.circuit.CircuitBreakerService#recordFailure} 调用本服务持久化。
 * 便于管理端「风控日志」页检索诊断 + 启动时熔断器从最近未冷却记录恢复状态。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>写日志走 REQUIRES_NEW，避免随业务事务回滚丢失风控记录</li>
 *   <li>冷却到期恢复：定时任务（或下次 allowRequest）标 recovered=1</li>
 *   <li>triggerType/riskCode 字段便于按风控类型筛选诊断</li>
 * </ul>
 */
@Service
public class RiskControlLogService {

    private static final Logger log = LoggerFactory.getLogger(RiskControlLogService.class);

    private final RiskControlLogMapper mapper;

    public RiskControlLogService(RiskControlLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 写一条风控日志。accountId/triggerType 必填，其余可空。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long accountId, String triggerType, String triggerScene,
                    String riskCode, String failureReason, Integer cooldownSeconds,
                    Long batchJobId) {
        RiskControlLog row = new RiskControlLog();
        row.setAccountId(accountId);
        row.setTriggerType(triggerType);
        row.setTriggerScene(triggerScene);
        row.setRiskCode(riskCode);
        row.setFailureReason(failureReason);
        row.setCooldownSeconds(cooldownSeconds);
        if (cooldownSeconds != null && cooldownSeconds > 0) {
            row.setCooldownUntil(LocalDateTime.now().plusSeconds(cooldownSeconds));
        }
        row.setBatchJobId(batchJobId);
        row.setTriggeredAt(LocalDateTime.now());
        row.setRecovered(0);
        try {
            mapper.insert(row);
        } catch (Exception e) {
            // 持久化失败不阻断主链路（熔断器内存态仍生效）
            log.warn("[A5] persist risk control log failed for account {}: {}", accountId, e.getMessage());
        }
    }

    /** 标记所有冷却已到期的记录为已恢复（recovered=1）。由定时任务或 allowRequest 调用。 */
    @Transactional
    public int markExpiredAsRecovered() {
        List<RiskControlLog> expired = mapper.selectList(new LambdaQueryWrapper<RiskControlLog>()
                .eq(RiskControlLog::getRecovered, 0)
                .isNotNull(RiskControlLog::getCooldownUntil)
                .le(RiskControlLog::getCooldownUntil, LocalDateTime.now()));
        for (RiskControlLog r : expired) {
            r.setRecovered(1);
            mapper.updateById(r);
        }
        return expired.size();
    }

    /** 分页查询账号的风控日志，可选 triggerType/recovered 过滤。给管理端「风控日志」页用。 */
    public Page<RiskControlLog> list(int page, int size, Long accountId, String triggerType, Integer recovered) {
        Page<RiskControlLog> p = new Page<>(page, size);
        LambdaQueryWrapper<RiskControlLog> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) wrapper.eq(RiskControlLog::getAccountId, accountId);
        if (triggerType != null && !triggerType.isBlank()) wrapper.eq(RiskControlLog::getTriggerType, triggerType);
        if (recovered != null) wrapper.eq(RiskControlLog::getRecovered, recovered);
        wrapper.orderByDesc(RiskControlLog::getTriggeredAt);
        return mapper.selectPage(p, wrapper);
    }

    /** 查某账号最近一条未恢复的风控日志（启动时熔断器从此恢复状态用）。 */
    public RiskControlLog latestUnrecovered(Long accountId) {
        return mapper.selectOne(new LambdaQueryWrapper<RiskControlLog>()
                .eq(RiskControlLog::getAccountId, accountId)
                .eq(RiskControlLog::getRecovered, 0)
                .isNotNull(RiskControlLog::getCooldownUntil)
                .gt(RiskControlLog::getCooldownUntil, LocalDateTime.now())
                .orderByDesc(RiskControlLog::getTriggeredAt)
                .last("LIMIT 1"));
    }
}
