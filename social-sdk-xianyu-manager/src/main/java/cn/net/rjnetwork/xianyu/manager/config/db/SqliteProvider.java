package cn.net.rjnetwork.xianyu.manager.config.db;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * SQLite 方言实现 — 默认数据库类型。
 * <p>WAL 模式下 SQLite 读不阻塞写、写不阻塞读，但<b>写仍单线程</b>（同时只有一个事务能写）。
 * {@code maxActive=2} 允许嵌套事务（外层 @Transactional + 内层 REQUIRES_NEW）各拿独立连接，
 * 又最小化写锁竞争；{@code busy_timeout=120000} 让等写锁别轻易超时。
 * 调大 maxActive 反而让多事务同时竞争写锁 → SQLITE_BUSY → 30s 超时（现网症结）。</p>
 * <p>激活条件：{@code bitefu.wall.db-type=sqlite} 或缺失（兜底默认）。</p>
 */
@Component
@ConditionalOnProperty(prefix = "bitefu.wall", name = "db-type", havingValue = "sqlite", matchIfMissing = true)
public class SqliteProvider implements DatabaseProvider {

    private static final String[] PRAGMA = {
            "PRAGMA journal_mode=WAL",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA busy_timeout=120000",
            "PRAGMA cache_size=-16000",
            "PRAGMA mmap_size=268435456",
            "PRAGMA temp_store=MEMORY",
            "PRAGMA foreign_keys=ON"
    };

    @Override public String dialect() { return "sqlite"; }
    @Override public String schemaFile() { return "db/schema-sqlite.sql"; }
    @Override public String[] connectionInitSqls() { return Arrays.copyOf(PRAGMA, PRAGMA.length); }
    @Override public int maxActive() { return 2; }
    @Override public String validationQuery() { return "SELECT 1"; }
    @Override public boolean supportsUpsert() { return true; }

    @PostConstruct
    void register() { DatabaseProviderHolder.INSTANCE = this; }
}
