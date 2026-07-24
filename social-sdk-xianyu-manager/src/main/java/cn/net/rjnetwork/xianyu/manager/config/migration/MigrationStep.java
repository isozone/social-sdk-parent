package cn.net.rjnetwork.xianyu.manager.config.migration;

import javax.sql.DataSource;

/**
 * 迁移步骤 —— I7 数据迁移框架的注册单元。
 * <p>每个整改阶段声明一个 MigrationStep 实现类，在 {@link MigrationExecutor#startup} 时按顺序执行：
 * 1) 查 schema_migration 表是否已跑过该 namespace+version；
 * 2) 已跑过则 skip；
 * 3) 未跑过则执行 {@link #migrate}，成功后写一行 schema_migration 记录；
 * 4) 失败则记录失败原因但**不中断启动**（避免迁移单点失败拖垮整个服务），由日志告警人工介入。</p>
 *
 * <p>实现类必须是幂等的：可重复执行不报错（用 IF NOT EXISTS / ensureColumn 兜底）。</p>
 */
public interface MigrationStep {

    /** 命名空间，如 "account" / "card" / "ship"，避免不同模块版本号冲突。 */
    String namespace();

    /** 版本号，如 "20260725_01"；同一 namespace 下唯一。 */
    String version();

    /** 人类可读描述，如「add cookie_refresh_schedule table」。 */
    String description();

    /**
     * 执行迁移。必须是幂等的。
     *
     * @param dataSource 当前数据源（dialect 由 DatabaseInitializer 决定）
     * @throws Exception 失败时抛出，框架会捕获并记录到 schema_migration.failure_reason
     */
    void migrate(DataSource dataSource) throws Exception;
}
