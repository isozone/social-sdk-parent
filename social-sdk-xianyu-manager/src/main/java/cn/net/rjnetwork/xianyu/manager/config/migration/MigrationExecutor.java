package cn.net.rjnetwork.xianyu.manager.config.migration;

import cn.net.rjnetwork.xianyu.manager.config.DatabaseInitializer;
import cn.net.rjnetwork.xianyu.manager.config.migration.SchemaMigrationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 迁移执行器 —— I7 数据迁移框架的运行入口。
 * <p>启动时由 {@link DatabaseInitializer} 调 {@link #startup}：
 * 1) 先确保 schema_migration 表存在（按 dialect 建表兜底）；
 * 2) 收集所有注入的 {@link MigrationStep} Bean，按 namespace/version 排序；
 * 3) 逐个执行：已跑过的 skip，未跑过的执行并写记录；
 * 4) 失败仅记录不中断启动，避免单点迁移失败拖垮整个服务。</p>
 *
 * <p>后续每个整改阶段（A/B/C/D/E/F/G/H）声明自己的 MigrationStep 实现类，
 * 自动被本执行器收集，无需手改启动流程。</p>
 */
@Component
public class MigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);

    private final SchemaMigrationMapper mapper;
    private final DataSource dataSource;
    private final List<MigrationStep> steps;

    public MigrationExecutor(SchemaMigrationMapper mapper, DataSource dataSource,
                              List<MigrationStep> steps) {
        this.mapper = mapper;
        this.dataSource = dataSource;
        this.steps = steps;
    }

    /** 启动时调用：按顺序幂等执行所有迁移步骤。 */
    public void startup() {
        ensureSchemaMigrationTable();
        if (steps == null || steps.isEmpty()) {
            log.info("[Migration] no migration step registered, skip");
            return;
        }
        steps.sort(Comparator
                .comparing(MigrationStep::namespace)
                .thenComparing(MigrationStep::version));
        int total = steps.size();
        int success = 0, skipped = 0, failed = 0;
        for (MigrationStep step : steps) {
            String ns = step.namespace();
            String ver = step.version();
            try {
                if (alreadyExecuted(ns, ver)) {
                    log.debug("[Migration] {}:{} already executed, skip", ns, ver);
                    skipped++;
                    continue;
                }
                long t0 = System.currentTimeMillis();
                step.migrate(dataSource);
                recordMigration(ns, ver, step.description(),
                        System.currentTimeMillis() - t0, "SUCCESS", null);
                log.info("[Migration] {}:{} {} executed", ns, ver, step.description());
                success++;
            } catch (Exception e) {
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                recordMigration(ns, ver, step.description(), 0L, "FAILED", reason);
                log.warn("[Migration] {}:{} {} failed: {}", ns, ver, step.description(), reason);
                failed++;
            }
        }
        log.info("[Migration] finished: {} steps (success={}, skipped={}, failed={})",
                total, success, skipped, failed);
    }

    /** 查询某 namespace+version 是否已成功执行。 */
    private boolean alreadyExecuted(String namespace, String version) {
        try {
            Long count = mapper.selectCount(new LambdaQueryWrapper<SchemaMigration>()
                    .eq(SchemaMigration::getNamespace, namespace)
                    .eq(SchemaMigration::getVersion, version)
                    .eq(SchemaMigration::getStatus, "SUCCESS"));
            return count != null && count > 0;
        } catch (Exception e) {
            // 表可能还没建，按未执行处理
            return false;
        }
    }

    /** 写一行迁移记录（成功/失败/skip）。 */
    private void recordMigration(String ns, String ver, String desc,
                                  Long durationMs, String status, String failureReason) {
        try {
            SchemaMigration row = new SchemaMigration();
            row.setNamespace(ns);
            row.setVersion(ver);
            row.setDescription(desc);
            row.setDurationMs(durationMs);
            row.setStatus(status);
            row.setFailureReason(failureReason);
            row.setExecutedAt(LocalDateTime.now());
            mapper.insert(row);
        } catch (Exception e) {
            // 表写入失败不中断启动（兜底已建表，但若 dialect 异常仍可能炸）
            log.warn("[Migration] record {}:{} failed: {}", ns, ver, e.getMessage());
        }
    }

    /**
     * 确保 schema_migration 表存在（按 dialect 建表兜底）。
     * 新库 schema*.sql 已建好，这里仅针对升级场景或空库。
     */
    private void ensureSchemaMigrationTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "schema_migration")) return;
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute(buildSchemaMigrationDdl(conn));
                log.info("[Migration] created table schema_migration");
            }
        } catch (Exception e) {
            log.warn("[Migration] ensureSchemaMigrationTable failed: {}", e.getMessage());
        }
    }

    /** 按 dialect 生成 schema_migration 建表 SQL。 */
    private String buildSchemaMigrationDdl(java.sql.Connection conn) throws Exception {
        String d = conn.getMetaData().getDatabaseProductName().toLowerCase();
        boolean sqlite = d.contains("sqlite");
        boolean postgres = d.contains("postgres");
        String idType = postgres ? "BIGSERIAL" : "INTEGER";
        String idPk = (postgres || d.contains("mysql"))
                ? idType + " PRIMARY KEY AUTO_INCREMENT"
                : idType + " PRIMARY KEY AUTOINCREMENT";
        return "CREATE TABLE schema_migration ("
                + "id " + idPk + ", "
                + "namespace VARCHAR(64) NOT NULL, "
                + "version VARCHAR(32) NOT NULL, "
                + "description VARCHAR(256), "
                + "duration_ms BIGINT, "
                + "status VARCHAR(16), "
                + "failure_reason VARCHAR(512), "
                + "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "deleted INTEGER DEFAULT 0"
                + ")";
    }

    private boolean tableExists(java.sql.Connection conn, String table) throws Exception {
        String d = conn.getMetaData().getDatabaseProductName().toLowerCase();
        String sql;
        if (d.contains("sqlite")) {
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'";
        } else if (d.contains("postgres")) {
            sql = "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='" + table + "' LIMIT 1";
        } else {
            sql = "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='" + table + "' LIMIT 1";
        }
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }
}
