package cn.net.rjnetwork.xianyu.manager.circuit;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.circuit.service.RiskControlLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 风控暂停保护 —— BOT-A6。
 *
 * <p>识别官方风控码后：</p>
 * <ol>
 *   <li>暂停账号任务（status=FROZEN，防死循环刷官方接口）；</li>
 *   <li>写 risk_control_log（中文运营摘要 + operator_action_required）；</li>
 *   <li>通知（由调用方接 NotifyEvent，本服务不直接发通知）；</li>
 *   <li>禁止自动狂重试滑块/登录（由 {@link CircuitBreakerService} 熔断器兜底）。</li>
 * </ol>
 *
 * <p>识别的风控码：</p>
 * <ul>
 *   <li>{@code FAIL_SYS_USER_VALIDATE} — 通用账号校验失败（滑块/验证码）</li>
 *   <li>{@code RGV587_ERROR} — 风控拦截（滑块/验证码）</li>
 *   <li>{@code punish} URL — 闲鱼风控 punish 链路</li>
 * </ul>
 */
@Service
public class RiskControlProtector {

    private static final Logger log = LoggerFactory.getLogger(RiskControlProtector.class);

    /** 风控码识别正则 */
    private static final Pattern RISK_PATTERN = Pattern.compile(
            "FAIL_SYS_USER_VALIDATE|RGV587_ERROR|punish|x5secdata", Pattern.CASE_INSENSITIVE);

    private final AccountMapper accountMapper;
    private final CircuitBreakerService circuitBreaker;
    private final RiskControlLogService riskLogService;

    public RiskControlProtector(AccountMapper accountMapper,
                                 CircuitBreakerService circuitBreaker,
                                 RiskControlLogService riskLogService) {
        this.accountMapper = accountMapper;
        this.circuitBreaker = circuitBreaker;
        this.riskLogService = riskLogService;
    }

    /**
     * 判断给定错误信息/响应是否为风控触发。
     *
     * @param raw 错误信息/响应文本
     * @return true=风控触发
     */
    public boolean isRiskControlTriggered(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return RISK_PATTERN.matcher(raw).find();
    }

    /**
     * 处理风控触发：暂停账号 + 写 risk_log + 熔断。
     *
     * <p>幂等：账号已 FROZEN 时仅追加 risk_log，不重复暂停。</p>
     *
     * @param accountId  账号 ID
     * @param scene      触发场景（POLISH/CLOSE_NOTICE/MESSAGE_SYNC 等）
     * @param riskCode   风控码（FAIL_SYS_USER_VALIDATE/RGV587_ERROR/punish）
     * @param rawError   原始错误信息（含 punish URL 等关键诊断信息）
     * @return true=已暂停账号；false=风控未触发或账号已暂停
     */
    @Transactional
    public boolean handleRiskControl(Long accountId, String scene, String riskCode, String rawError) {
        if (!isRiskControlTriggered(rawError) && !isRiskControlTriggered(riskCode)) {
            return false;
        }
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null) {
            log.warn("[BOT-A6] account {} 不存在，跳过风控处理", accountId);
            return false;
        }
        // 中文运营摘要
        String summary = buildOperatorSummary(scene, riskCode, rawError);
        // 暂停账号（FROZEN），防死循环刷官方接口
        boolean wasActive = !"FROZEN".equals(acc.getStatus()) && !"DISABLED".equals(acc.getStatus());
        if (wasActive) {
            acc.setStatus("FROZEN");
            acc.setLastError(summary);
            acc.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(acc);
            log.warn("[BOT-A6] 账号 {} 因风控触发暂停（FROZEN），scene={}, riskCode={}",
                    accountId, scene, riskCode);
        }
        // 写 risk_control_log
        try {
            riskLogService.log(
                    accountId,
                    "RISK_CONTROL",
                    scene,
                    riskCode,
                    summary,
                    3600, // cooldownSeconds 默认冷却 1 小时
                    null  // batchJobId 由调用方传
            );
        } catch (Exception e) {
            log.warn("[BOT-A6] 写 risk_control_log 失败（非致命）: {}", e.getMessage());
        }
        // 熔断器记 failure（让后续请求在冷却期内被拦截）
        circuitBreaker.recordFailure(accountId, scene, summary);
        return wasActive;
    }

    /**
     * 构建中文运营摘要（含 operator_action_required 提示）。
     */
    private String buildOperatorSummary(String scene, String riskCode, String rawError) {
        String action;
        if ("punish".equalsIgnoreCase(riskCode) || (rawError != null && rawError.contains("punish"))) {
            action = "需要人工完成滑块验证后恢复账号";
        } else if ("FAIL_SYS_USER_VALIDATE".equalsIgnoreCase(riskCode)) {
            action = "需要人工验证账号身份后恢复";
        } else if ("RGV587_ERROR".equalsIgnoreCase(riskCode)) {
            action = "账号被风控拦截，需要人工处理后恢复";
        } else {
            action = "需要人工检查后恢复";
        }
        String truncated = rawError != null && rawError.length() > 200
                ? rawError.substring(0, 200) : (rawError != null ? rawError : "");
        return String.format("[operator_action_required] 场景=%s 风控码=%s 原始=%s 提示=%s",
                scene, riskCode, truncated, action);
    }
}
