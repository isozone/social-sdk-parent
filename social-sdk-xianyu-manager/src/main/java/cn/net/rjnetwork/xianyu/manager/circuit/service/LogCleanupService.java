package cn.net.rjnetwork.xianyu.manager.circuit.service;

import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledCookiesRefreshLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledLoginRenewLogMapper;
import cn.net.rjnetwork.xianyu.manager.account.renew.mapper.ScheduledTokenRenewalLogMapper;
import cn.net.rjnetwork.xianyu.manager.audit.mapper.AuditLogMapper;
import cn.net.rjnetwork.xianyu.manager.circuit.mapper.RiskControlLogMapper;
import cn.net.rjnetwork.xianyu.manager.message.closenotice.mapper.ScheduledCloseNoticeLogMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRateLogMapper;
import cn.net.rjnetwork.xianyu.manager.order.rate.mapper.ScheduledRedFlowerLogMapper;
import cn.net.rjnetwork.xianyu.manager.product.polish.mapper.ScheduledPolishLogMapper;
import cn.net.rjnetwork.xianyu.manager.reply.mapper.AutoReplyLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志清理服务 —— 按天删除过期日志记录，防止数据库膨胀。
 * <p>支持手动清理（API）和定时自动清理两种模式。</p>
 * <p>
 * 配置项（application.yml）：
 * <pre>
 * log-cleanup:
 *   enabled: true
 *   keep-days: 7                    # 保留最近 N 天的日志
 *   cron: "0 0 3 * * ?"             # 每天凌晨 3 点执行
 *   tables:                         # 需清理的日志表及时间字段
 *     risk_control_log: triggered_at
 *     circuit_breaker_event: created_at
 *     audit_log: action_time
 *     scheduled_login_renew_log: started_at
 *     scheduled_cookies_refresh_log: started_at
 *     scheduled_token_renewal_log: started_at
 *     scheduled_polish_log: started_at
 *     scheduled_close_notice_log: started_at
 *     scheduled_rate_log: started_at
 *     scheduled_red_flower_log: started_at
 *     xianyu_auto_reply_log: created_at
 *     notify_log: created_at
 * </pre>
 */
@Service
public class LogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupService.class);

    private final JdbcTemplate jdbc;

    /** 各日志表的 Mapper，用于统计保留记录数 */
    private final RiskControlLogMapper riskControlLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final ScheduledLoginRenewLogMapper loginRenewLogMapper;
    private final ScheduledCookiesRefreshLogMapper cookiesRefreshLogMapper;
    private final ScheduledTokenRenewalLogMapper tokenRenewalLogMapper;
    private final ScheduledPolishLogMapper polishLogMapper;
    private final ScheduledCloseNoticeLogMapper closeNoticeLogMapper;
    private final ScheduledRateLogMapper rateLogMapper;
    private final ScheduledRedFlowerLogMapper redFlowerLogMapper;
    private final AutoReplyLogMapper autoReplyLogMapper;
    private final NotifyLogMapper notifyLogMapper;

    /** 清理保留天数，默认 7 天 */
    @Value("${log-cleanup.keep-days:7}")
    private int keepDays;

    /** 是否启用自动清理，默认 true */
    @Value("${log-cleanup.enabled:true}")
    private boolean enabled;

    public LogCleanupService(JdbcTemplate jdbc,
                             RiskControlLogMapper riskControlLogMapper,
                             AuditLogMapper auditLogMapper,
                             ScheduledLoginRenewLogMapper loginRenewLogMapper,
                             ScheduledCookiesRefreshLogMapper cookiesRefreshLogMapper,
                             ScheduledTokenRenewalLogMapper tokenRenewalLogMapper,
                             ScheduledPolishLogMapper polishLogMapper,
                             ScheduledCloseNoticeLogMapper closeNoticeLogMapper,
                             ScheduledRateLogMapper rateLogMapper,
                             ScheduledRedFlowerLogMapper redFlowerLogMapper,
                             AutoReplyLogMapper autoReplyLogMapper,
                             NotifyLogMapper notifyLogMapper) {
        this.jdbc = jdbc;
        this.riskControlLogMapper = riskControlLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.loginRenewLogMapper = loginRenewLogMapper;
        this.cookiesRefreshLogMapper = cookiesRefreshLogMapper;
        this.tokenRenewalLogMapper = tokenRenewalLogMapper;
        this.polishLogMapper = polishLogMapper;
        this.closeNoticeLogMapper = closeNoticeLogMapper;
        this.rateLogMapper = rateLogMapper;
        this.redFlowerLogMapper = redFlowerLogMapper;
        this.autoReplyLogMapper = autoReplyLogMapper;
        this.notifyLogMapper = notifyLogMapper;
    }

    /**
     * 定时自动清理：每天执行一次，删除 N 天前的日志记录。
     */
    @Scheduled(cron = "${log-cleanup.cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        if (!enabled) {
            log.debug("[LOG-CLEANUP] 自动清理已禁用，跳过");
            return;
        }
        log.info("[LOG-CLEANUP] 开始定时清理，保留最近 {} 天", keepDays);
        try {
            Map<String, Integer> result = cleanupAll(keepDays);
            log.info("[LOG-CLEANUP] 清理完成: {}", result);
        } catch (Exception e) {
            log.error("[LOG-CLEANUP] 清理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理所有支持的日志表，删除指定天数之前的记录。
     *
     * @param keepDays 保留最近多少天
     * @return 各表的清理结果 {tableName: deletedCount}
     */
    @Transactional
    public Map<String, Integer> cleanupAll(int keepDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        Map<String, Integer> result = new HashMap<>();

        // risk_control_log
        int deleted = deleteBefore("risk_control_log", "triggered_at", cutoff);
        result.put("risk_control_log", deleted);

        // circuit_breaker_event
        deleted = deleteBefore("circuit_breaker_event", "created_at", cutoff);
        result.put("circuit_breaker_event", deleted);

        // audit_log
        deleted = deleteBefore("audit_log", "action_time", cutoff);
        result.put("audit_log", deleted);

        // scheduled_login_renew_log
        deleted = deleteBefore("scheduled_login_renew_log", "started_at", cutoff);
        result.put("scheduled_login_renew_log", deleted);

        // scheduled_cookies_refresh_log
        deleted = deleteBefore("scheduled_cookies_refresh_log", "started_at", cutoff);
        result.put("scheduled_cookies_refresh_log", deleted);

        // scheduled_token_renewal_log
        deleted = deleteBefore("scheduled_token_renewal_log", "started_at", cutoff);
        result.put("scheduled_token_renewal_log", deleted);

        // scheduled_polish_log
        deleted = deleteBefore("scheduled_polish_log", "started_at", cutoff);
        result.put("scheduled_polish_log", deleted);

        // scheduled_close_notice_log
        deleted = deleteBefore("scheduled_close_notice_log", "started_at", cutoff);
        result.put("scheduled_close_notice_log", deleted);

        // scheduled_rate_log
        deleted = deleteBefore("scheduled_rate_log", "started_at", cutoff);
        result.put("scheduled_rate_log", deleted);

        // scheduled_red_flower_log
        deleted = deleteBefore("scheduled_red_flower_log", "started_at", cutoff);
        result.put("scheduled_red_flower_log", deleted);

        // xianyu_auto_reply_log
        deleted = deleteBefore("xianyu_auto_reply_log", "created_at", cutoff);
        result.put("xianyu_auto_reply_log", deleted);

        // notify_log
        deleted = deleteBefore("notify_log", "created_at", cutoff);
        result.put("notify_log", deleted);

        return result;
    }

    /**
     * 删除指定表中早于 cutoff 时间的记录。
     * 使用原生 SQL 绕过 MyBatis-Plus 的逻辑删除（deleted 字段）。
     */
    private int deleteBefore(String table, String timeColumn, LocalDateTime cutoff) {
        String sql = "DELETE FROM " + table + " WHERE " + timeColumn + " < ?";
        try {
            int rows = jdbc.update(sql, cutoff);
            if (rows > 0) {
                log.info("[LOG-CLEANUP] 删除 {} 表 {} 条记录（早于 {}）", table, rows, cutoff);
            }
            return rows;
        } catch (Exception e) {
            log.warn("[LOG-CLEANUP] 删除 {} 表记录失败: {}", table, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取各日志表的统计信息（总记录数和最近记录时间）。
     */
    public Map<String, Object> getLogStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("risk_control_log", getTableStat("risk_control_log", "triggered_at"));
        stats.put("circuit_breaker_event", getTableStat("circuit_breaker_event", "created_at"));
        stats.put("audit_log", getTableStat("audit_log", "action_time"));
        stats.put("scheduled_login_renew_log", getTableStat("scheduled_login_renew_log", "started_at"));
        stats.put("scheduled_cookies_refresh_log", getTableStat("scheduled_cookies_refresh_log", "started_at"));
        stats.put("scheduled_token_renewal_log", getTableStat("scheduled_token_renewal_log", "started_at"));
        stats.put("scheduled_polish_log", getTableStat("scheduled_polish_log", "started_at"));
        stats.put("scheduled_close_notice_log", getTableStat("scheduled_close_notice_log", "started_at"));
        stats.put("scheduled_rate_log", getTableStat("scheduled_rate_log", "started_at"));
        stats.put("scheduled_red_flower_log", getTableStat("scheduled_red_flower_log", "started_at"));
        stats.put("xianyu_auto_reply_log", getTableStat("xianyu_auto_reply_log", "created_at"));
        stats.put("notify_log", getTableStat("notify_log", "created_at"));

        return stats;
    }

    /**
     * 获取单个表的统计信息。
     */
    private Map<String, Object> getTableStat(String table, String timeColumn) {
        Map<String, Object> stat = new HashMap<>();
        try {
            // 总记录数
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            stat.put("total", total != null ? total : 0);

            // 最近一条记录时间
            String latestSql = "SELECT MAX(" + timeColumn + ") FROM " + table;
            Object latest = jdbc.queryForObject(latestSql, Object.class);
            stat.put("latest", latest);

            // N 天前的记录数（可被清理的数量）
            LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
            String deletableSql = "SELECT COUNT(*) FROM " + table + " WHERE " + timeColumn + " < ?";
            Integer deletable = jdbc.queryForObject(deletableSql, Integer.class, cutoff);
            stat.put("deletable", deletable != null ? deletable : 0);
        } catch (Exception e) {
            stat.put("total", 0);
            stat.put("latest", null);
            stat.put("deletable", 0);
        }
        return stat;
    }

    public int getKeepDays() {
        return keepDays;
    }

    public void setKeepDays(int keepDays) {
        this.keepDays = keepDays;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
