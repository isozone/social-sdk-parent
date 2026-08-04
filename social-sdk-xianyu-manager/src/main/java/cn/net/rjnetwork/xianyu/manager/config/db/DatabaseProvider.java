package cn.net.rjnetwork.xianyu.manager.config.db;

/**
 * 数据库方言抽象 — 支持 SQLite（默认）/ MySQL8 / PostgreSQL 三选一。
 * <p>由显式配置 {@code bitefu.wall.db-type=sqlite|mysql|postgres} 激活对应实现。
 * 调用方通过 {@link #current()} 拿到当前实现，获取方言专属的初始化 SQL / schema 文件名 / 验证查询。</p>
 *
 * <p>三选一规则：数据库类型只看 {@code bitefu.wall.db-type}，默认 sqlite。
 * {@code spring.profiles.active} 只负责加载配置文件，不作为数据库类型判断标准。</p>
 */
public interface DatabaseProvider {

    /** 方言标识：sqlite / mysql / postgres */
    String dialect();

    /** schema 文件名（classpath:db/ 下），如 schema-sqlite.sql / schema-mysql.sql / schema-postgres.sql */
    String schemaFile();

    /** Druid 连接初始化 SQL（SQLite 走 PRAGMA，MySQL/PG 走 SET 优化） */
    String[] connectionInitSqls();

    /** Druid 最大活动连接数（SQLite WAL 模式下 2（最小化写锁竞争），MySQL/PG 可更高） */
    int maxActive();

    /** Druid 验证查询（SQLite 用 SELECT 1，MySQL/PG 也兼容） */
    String validationQuery();

    /** 是否支持 INSERT ... ON CONFLICT/ON DUPLICATE KEY UPDATE（SQLite/PG 支持，MySQL 用 ON DUPLICATE KEY UPDATE） */
    boolean supportsUpsert();

    /** 当前生效的实现（由 Spring 注入的实例提供） */
    static DatabaseProvider current() {
        return DatabaseProviderHolder.INSTANCE;
    }

    /** Spring 注入用：由各实现的 @PostConstruct 把自己塞进 holder */
    class DatabaseProviderHolder {
        static volatile DatabaseProvider INSTANCE;
    }
}
