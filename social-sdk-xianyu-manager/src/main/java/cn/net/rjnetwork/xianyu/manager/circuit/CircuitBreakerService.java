package cn.net.rjnetwork.xianyu.manager.circuit;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.circuit.service.RiskControlLogService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器服务 — 按账号+服务维度管理熔断状态
 */
@Service
public class CircuitBreakerService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final JdbcTemplate jdbc;
    private final RiskControlLogService riskControlLogService;
    private final AccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, CircuitBreaker> cache = new ConcurrentHashMap<>();

    private static final RowMapper<CircuitBreaker> MAPPER = (rs, rowNum) -> {
        CircuitBreaker cb = new CircuitBreaker();
        cb.setId(rs.getLong("id"));
        long acctId = rs.getLong("account_id");
        cb.setAccountId(rs.wasNull() ? null : acctId);
        cb.setServiceName(rs.getString("service_name"));
        cb.setState(rs.getString("state"));
        cb.setFailureCount(rs.getInt("failure_count"));
        cb.setSuccessCount(rs.getInt("success_count"));
        cb.setLastFailureAt(rs.getObject("last_failure_at", LocalDateTime.class));
        cb.setLastFailureMessage(rs.getString("last_failure_message"));
        cb.setLastSuccessAt(rs.getObject("last_success_at", LocalDateTime.class));
        cb.setOpenedAt(rs.getObject("opened_at", LocalDateTime.class));
        cb.setCooldownUntil(rs.getObject("cooldown_until", LocalDateTime.class));
        cb.setThresholdCount(rs.getInt("threshold_count"));
        cb.setCooldownSeconds(rs.getInt("cooldown_seconds"));
        cb.setHalfOpenMaxSuccess(rs.getInt("half_open_max_success"));
        cb.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        cb.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return cb;
    };

    public CircuitBreakerService(JdbcTemplate jdbc, RiskControlLogService riskControlLogService,
                                 AccountMapper accountMapper, ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.riskControlLogService = riskControlLogService;
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 检查是否允许执行（熔断器未开闸）
     */
    public boolean allowRequest(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        if (cb.isClosed()) return true;
        if (cb.isOpen()) {
            if (!cb.isInCooldown()) {
                // 冷却结束，进入半开
                cb.setState("HALF_OPEN");
                cb.setSuccessCount(0);
                persist(cb);
                return true;
            }
            return false;
        }
        // HALF_OPEN 允许探测
        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        boolean wasActive = cb.isOpen() || cb.isHalfOpen();
        cb.recordSuccess();
        persist(cb);
        // 闭环：开闸/半开 -> 关闭 的跃迁视为恢复，推送通知
        if (wasActive && cb.isClosed()) {
            publishRecovered(accountId, serviceName, cb);
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure(Long accountId, String serviceName, String message) {
        // 服务器过载（闲鱼"被挤爆啦"提示）不是真实风控，不应累加熔断器失败计数
        if (isServerOverload(message)) {
            logger.debug("Circuit breaker skip overload for account={} service={}: {}", accountId, serviceName, truncate(message, 80));
            return;
        }
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        boolean wasOpen = cb.isOpen();
        cb.recordFailure(message);
        persist(cb);
        if (cb.isOpen()) {
            logger.warn("Circuit breaker OPENED for account={} service={} after {} failures",
                    accountId, serviceName, cb.getFailureCount());
            // A5 风控日志持久化：OPEN 时写一行 risk_control_log，便于管理端检索诊断
            // 触发类型按serviceName推断（COOKIE_RENEW/TOKEN_RENEWAL/LOGIN_RENEW/MESSAGE_SEND/MTOP_CALL）
            String triggerType = inferTriggerType(serviceName);
            String riskCode = extractRiskCode(message);
            int cooldown = Optional.ofNullable(cb.getCooldownSeconds()).orElse(300);
            riskControlLogService.log(accountId, triggerType, serviceName, riskCode,
                    message, cooldown, null);
            // 闭环：仅在「未开闸 -> 开闸」的跃迁时推送通知，避免持续失败反复轰炸
            if (!wasOpen) {
                publishOpened(accountId, serviceName, cb, message);
            }
        }
    }

    /** 判断是否为服务器过载（闲鱼"被挤爆啦"等提示），非真实风控 */
    private boolean isServerOverload(String message) {
        if (message == null || message.isBlank()) return false;
        return message.matches("(?i).*挤爆|过载|限流|server\\.busy|too\\.many\\.request|请稍后重试.*");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /** 按 serviceName 推断触发类型。 */
    private String inferTriggerType(String serviceName) {
        if (serviceName == null) return "UNKNOWN";
        if (serviceName.contains("TOKEN")) return "TOKEN_EXPIRED";
        if (serviceName.contains("LOGIN")) return "LOGIN_FAILED";
        if (serviceName.contains("MESSAGE")) return "USER_VALIDATE";
        if (serviceName.contains("RATE")) return "RATE_LIMIT";
        return "CAPTCHA"; // 默认归为滑块风控
    }

    /** 从失败消息里抽风控原始标识（RGV587_ERROR::SM / FAIL_SYS_USER_VALIDATE 等）。 */
    private String extractRiskCode(String message) {
        if (message == null) return null;
        String[] markers = {"RGV587_ERROR", "FAIL_SYS_USER_VALIDATE", "FAIL_SYS_RATE_LIMIT",
                "FAIL_SYS_TOKEN_EXOIRED", "FAIL_SYS_TOKEN_EMPTY", "x5sec", "punish"};
        for (String m : markers) {
            if (message.contains(m)) return m;
        }
        return null;
    }

    /**
     * 重置熔断器
     */
    public void reset(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        cb.reset();
        persist(cb);
    }

    /** 闭环：熔断器开闸时推送 CIRCUIT_BREAKER_OPENED 通知 */
    private void publishOpened(Long accountId, String serviceName, CircuitBreaker cb, String message) {
        try {
            String accountName = resolveAccountName(accountId);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("serviceName", serviceName);
            vars.put("accountName", accountName);
            vars.put("failureCount", String.valueOf(cb.getFailureCount()));
            vars.put("cooldownSeconds", String.valueOf(cb.getCooldownSeconds()));
            vars.put("lastError", message != null ? truncate(message, 200) : "");
            eventPublisher.publishEvent(new NotifyEvent("CIRCUIT_BREAKER_OPENED", accountId, accountName, vars));
        } catch (Exception e) {
            logger.warn("[CIRCUIT] 推送 OPENED 通知失败: {}", e.getMessage());
        }
    }

    /** 闭环：熔断器恢复（开闸/半开 -> 关闭）时推送 CIRCUIT_BREAKER_RECOVERED 通知 */
    private void publishRecovered(Long accountId, String serviceName, CircuitBreaker cb) {
        try {
            String accountName = resolveAccountName(accountId);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("serviceName", serviceName);
            vars.put("accountName", accountName);
            eventPublisher.publishEvent(new NotifyEvent("CIRCUIT_BREAKER_RECOVERED", accountId, accountName, vars));
        } catch (Exception e) {
            logger.warn("[CIRCUIT] 推送 RECOVERED 通知失败: {}", e.getMessage());
        }
    }

    private String resolveAccountName(Long accountId) {
        if (accountId == null) return "GLOBAL";
        try {
            XianyuAccount acc = accountMapper.selectById(accountId);
            if (acc != null) {
                return acc.getDisplayName() != null ? acc.getDisplayName() : acc.getAccountName();
            }
        } catch (Exception ignored) {}
        return String.valueOf(accountId);
    }

    public List<CircuitBreaker> listAll() {
        return jdbc.query("SELECT * FROM circuit_breaker ORDER BY id", MAPPER);
    }

    public CircuitBreaker get(Long accountId, String serviceName) {
        return getOrCreate(accountId, serviceName);
    }

    private CircuitBreaker getOrCreate(Long accountId, String serviceName) {
        String key = (accountId == null ? "GLOBAL" : accountId) + ":" + serviceName;
        return cache.computeIfAbsent(key, k -> {
            List<CircuitBreaker> list;
            if (accountId == null) {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id IS NULL AND service_name = ?",
                        MAPPER, serviceName);
            } else {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id = ? AND service_name = ?",
                        MAPPER, accountId, serviceName);
            }
            if (!list.isEmpty()) return list.get(0);
            // 创建新记录
            CircuitBreaker cb = new CircuitBreaker();
            cb.setAccountId(accountId);
            cb.setServiceName(serviceName);
            cb.setState("CLOSED");
            cb.setThresholdCount(5);
            cb.setCooldownSeconds(300);
            cb.setHalfOpenMaxSuccess(3);
            persist(cb);
            return cb;
        });
    }

    private void persist(CircuitBreaker cb) {
        cb.setUpdatedAt(LocalDateTime.now());
        if (cb.getId() == null) {
            String sql = "INSERT INTO circuit_breaker (account_id, service_name, state, failure_count, success_count, " +
                    "last_failure_at, last_failure_message, last_success_at, opened_at, cooldown_until, " +
                    "threshold_count, cooldown_seconds, half_open_max_success, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbc.update(sql, cb.getAccountId(), cb.getServiceName(), cb.getState(),
                    cb.getFailureCount(), cb.getSuccessCount(),
                    cb.getLastFailureAt(), cb.getLastFailureMessage(), cb.getLastSuccessAt(),
                    cb.getOpenedAt(), cb.getCooldownUntil(),
                    cb.getThresholdCount(), cb.getCooldownSeconds(), cb.getHalfOpenMaxSuccess(),
                    LocalDateTime.now(), cb.getUpdatedAt());
            // 重新查询获取 id
            List<CircuitBreaker> list;
            if (cb.getAccountId() == null) {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id IS NULL AND service_name = ? ORDER BY id DESC LIMIT 1",
                        MAPPER, cb.getServiceName());
            } else {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id = ? AND service_name = ? ORDER BY id DESC LIMIT 1",
                        MAPPER, cb.getAccountId(), cb.getServiceName());
            }
            if (!list.isEmpty()) cb.setId(list.get(0).getId());
        } else {
            String sql = "UPDATE circuit_breaker SET state=?, failure_count=?, success_count=?, " +
                    "last_failure_at=?, last_failure_message=?, last_success_at=?, opened_at=?, cooldown_until=?, " +
                    "threshold_count=?, cooldown_seconds=?, half_open_max_success=?, updated_at=? WHERE id=?";
            jdbc.update(sql, cb.getState(), cb.getFailureCount(), cb.getSuccessCount(),
                    cb.getLastFailureAt(), cb.getLastFailureMessage(), cb.getLastSuccessAt(),
                    cb.getOpenedAt(), cb.getCooldownUntil(),
                    cb.getThresholdCount(), cb.getCooldownSeconds(), cb.getHalfOpenMaxSuccess(),
                    cb.getUpdatedAt(), cb.getId());
        }
        // 记录事件
        String evtSql = "INSERT INTO circuit_breaker_event (breaker_id, event_type, message, created_at) VALUES (?, ?, ?, ?)";
        jdbc.update(evtSql, cb.getId(), "STATE_CHANGE",
                "state=" + cb.getState() + " failures=" + cb.getFailureCount(), LocalDateTime.now());
    }
}
