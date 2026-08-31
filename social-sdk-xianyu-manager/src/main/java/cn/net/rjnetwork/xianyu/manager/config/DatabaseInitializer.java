package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.config.migration.MigrationExecutor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库初始化配置
 * 启动时自动执行 schema.sql 初始化数据库表结构
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final AuthService authService;
    private final cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider databaseProvider;
    private final MigrationExecutor migrationExecutor;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** 当前方言（sqlite/mysql/postgres），databaseProvider 为空时兜底 sqlite */
    private String dialect() {
        return databaseProvider != null ? databaseProvider.dialect().toLowerCase() : "sqlite";
    }

    /** 是否 SQLite 方言 */
    private boolean isSqlite() { return "sqlite".equals(dialect()); }

    /**
     * 查表是否存在（多方言）。
     * SQLite: sqlite_master；MySQL/PG: information_schema.tables。
     * MySQL 的 table_schema 是库名，用 DATABASE()；PG 的 table_schema 是 public。
     */
    private boolean tableExists(java.sql.Connection conn, String table) {
        String sql = isSqlite()
                ? "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'"
                : "SELECT 1 FROM information_schema.tables WHERE table_schema = "
                    + (dialect().equals("postgres") ? "'public'" : "DATABASE()")
                    + " AND table_name = '" + table + "' LIMIT 1";
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean indexExists(java.sql.Connection conn, String table, String indexName) {
        if (indexName == null || indexName.isBlank()) return false;
        String sql;
        if (isSqlite()) {
            sql = "SELECT name FROM sqlite_master WHERE type='index' AND name='" + indexName + "'";
        } else if ("postgres".equals(dialect())) {
            sql = "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + indexName + "' LIMIT 1";
        } else {
            sql = "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() "
                    + "AND index_name = '" + indexName + "'"
                    + (table == null || table.isBlank() ? "" : " AND table_name = '" + table + "'")
                    + " LIMIT 1";
        }
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查列是否存在（多方言）。
     * SQLite: sqlite_master 的 sql 字段正则匹配；MySQL/PG: information_schema.columns。
     */
    private boolean columnExists(java.sql.Connection conn, String table, String column) {
        if (isSqlite()) {
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery(
                         "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                if (rs.next()) {
                    String createSql = rs.getString(1);
                    if (createSql == null) return false;
                    return java.util.regex.Pattern.compile(
                            "\\b" + java.util.regex.Pattern.quote(column) + "\\b",
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(createSql).find();
                }
            } catch (Exception ignored) {
            }
            return false;
        }
        // MySQL/PG: information_schema.columns
        String sql = "SELECT 1 FROM information_schema.columns WHERE table_schema = "
                + (dialect().equals("postgres") ? "'public'" : "DATABASE()")
                + " AND table_name = '" + table + "' AND column_name = '" + column + "' LIMIT 1";
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (Exception ignored) {
            return false;
        }
    }

    public DatabaseInitializer(DataSource dataSource, AuthService authService,
                               cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider databaseProvider,
                               MigrationExecutor migrationExecutor) {
        this.dataSource = dataSource;
        this.authService = authService;
        this.databaseProvider = databaseProvider;
        this.migrationExecutor = migrationExecutor;
    }

    @PostConstruct
    public void init() {
        try {
            String dbPath = System.getProperty("user.dir") + "/data";
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            ClassPathResource resource = new ClassPathResource(
                    databaseProvider != null ? databaseProvider.schemaFile() : "db/schema-sqlite.sql");
            if (resource.exists()) {
                executeSchemaPerStatement();
                logger.info("Database schema initialized successfully (dialect={})",
                        databaseProvider != null ? databaseProvider.dialect() : "sqlite(fallback)");
            } else {
                // 兜底：profile 指定的 schema 文件不存在时退回默认 schema.sql（向后兼容）
                logger.warn("Schema file not found, fallback to db/schema.sql");
                new ClassPathResource("db/schema.sql");
                executeSchemaPerStatement();
            }
        } catch (Exception e) {
            logger.warn("Database schema initialization skipped (may already exist): {}", e.getMessage());
        }

        // ===== 旧库列补齐链路 =====
        // 独立 try：executeSchemaFile 遇到硬错会冒泡，若与 ensure* 同处一个 try，
        // 整条兜底链会被跳过 → 老库缺列（如 xianyu_account.last_keepalive_at）永远补不上，
        // 运行期持续抛 "Unknown column 'xxx' in 'field list'"。
        // ensure* 内部已对表/列存在性做幂等判断，schema 失败时它们只是空跑或按 tableExists 跳过。
        try {
            ensureNotifyRetryColumns();
            ensureNotifyDigestConfigTable();
            ensureProductColumns();
            ensureAiColumns();
            ensureVirtualColumns();
            ensureOrderColumns();
            ensureMessageColumns();
            ensureOpenAppTable();
            ensureImColumns();
            ensureAccountIdentityFromCookie();
            ensureRuleColumns();
            // ===== 新增模块的列补齐 =====
            ensureMarketColumns();
            ensureMarketKeywordTable();
            ensureMonitorColumns();
            ensureBuyerProfileColumns();
            ensureCircuitBreakerColumns();
            ensureAdminUserColumns();
            ensureAiCsSessionStateColumns();
            ensureAutoReplyLogTable();
            ensureVipTables();
            // ===== B9 批次日志框架：batch_job / batch_job_item 旧库补建 =====
            ensureBatchTables();

            // ===== proxy 模块表初始化（proxy_account_binding / proxy_cool_down / proxy_audit_log）=====
            // proxy 模块的 schema 文件在 social-sdk-proxys/db/proxy-bindings.sql，
            // 但本类只加载 manager 自己的 schema，proxy 表不会被建 → 启动时 findAllActive 抛 no such table。
            // 这里额外执行 proxy schema，保证删库重建时所有表都建好。
            // 单独 try：proxy schema 抛错（方言/权限）不能连带跳过后面的账号列补齐。
            try {
                executeProxySchema();
            } catch (Exception e) {
                logger.warn("Proxy schema initialization skipped: {}", e.getMessage());
            }
            // ===== 账号会话/资料字段 + Chrome 容器隔离字段补齐（旧库升级）=====
            // ensureColumn 内部已做 tableExists 兜底，主 schema 失败时空库不会对着不存在的表 ALTER。
            ensureAccountColumns();
            ensureChromeColumns();
            // 本地商品运费偏好列（shipping_mode）旧库升级兜底
            ensureLocalProductShippingModeColumn();
        } catch (Exception e) {
            logger.warn("Database column backfill skipped: {}", e.getMessage());
        }

        try {
            authService.initDefaultAdmin("admin", "admin123");
            logger.info("Default admin account initialized (username: admin, password: admin123)");
        } catch (Exception e) {
            logger.warn("Admin initialization skipped: {}", e.getMessage());
        }

        // ===== I7 数据迁移：启动时幂等执行所有 MigrationStep Bean =====
        // 必须在主 schema + ensure* 补列之后跑，避免迁移对着不存在的表 ALTER 会炸。
        // 失败仅记录不中断启动，由 schema_migration.failure_reason 告警人工介入。
        try {
            migrationExecutor.startup();
        } catch (Exception e) {
            logger.warn("Schema migration startup hook failed (non-fatal): {}", e.getMessage());
        }
    }

    private void ensureNotifyRetryColumns() {
        ensureColumn("notify_retry", "vars_json", "TEXT");
        // notify_log 投递日志表早期 schema 缺 sent_at 列（schema-*.sql 新建库已含，此处兜底老库升级），
        // 缺则 NotifyLogService 落库时报 "column 'sent_at' not found"，需用户手工 ALTER 很不友好。
        ensureColumn("notify_log", "sent_at", "TIMESTAMP");
    }

    private void ensureNotifyDigestConfigTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "notify_digest_config")) {
                return;
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute(createNotifyDigestConfigSql());
                logger.info("Created missing table notify_digest_config (dialect={})", dialect());
            }
        } catch (Exception e) {
            logger.warn("ensureNotifyDigestConfigTable skipped: {}", e.getMessage());
        }
    }

    /** notify_digest_config 建表 SQL（按方言分发） */
    private String createNotifyDigestConfigSql() {
        String d = dialect();
        switch (d) {
            case "mysql":
                return "CREATE TABLE notify_digest_config ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "enabled TINYINT(1) DEFAULT 0, "
                        + "channel_id INTEGER, "
                        + "recipients TEXT, "
                        + "hour INTEGER DEFAULT 9, "
                        + "minute INTEGER DEFAULT 0, "
                        + "scenarios TEXT, "
                        + "include_in_app TINYINT(1) DEFAULT 1, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            case "postgres":
                return "CREATE TABLE notify_digest_config ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "enabled BOOLEAN DEFAULT FALSE, "
                        + "channel_id INTEGER, "
                        + "recipients TEXT, "
                        + "hour INTEGER DEFAULT 9, "
                        + "minute INTEGER DEFAULT 0, "
                        + "scenarios TEXT, "
                        + "include_in_app BOOLEAN DEFAULT TRUE, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")";
            default:
                return "CREATE TABLE notify_digest_config ("
                        + "id INTEGER PRIMARY KEY, "
                        + "enabled BOOLEAN DEFAULT FALSE, "
                        + "channel_id INTEGER, "
                        + "recipients TEXT, "
                        + "hour INTEGER DEFAULT 9, "
                        + "minute INTEGER DEFAULT 0, "
                        + "scenarios TEXT, "
                        + "include_in_app BOOLEAN DEFAULT TRUE, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                        + ")";
        }
    }

    private void executeSchemaPerStatement() throws Exception {
        String schemaFile = databaseProvider != null ? databaseProvider.schemaFile() : "db/schema-sqlite.sql";
        executeSchemaFile(schemaFile);
    }

    /**
     * 执行 proxy 模块的 schema 文件，建 proxy_config / proxy_account_binding / proxy_cool_down / proxy_audit_log。
     * proxy 模块的 schema 在 social-sdk-proxys 里，本类只加载 manager 自己的 schema，proxy 表不会被建；
     * 启动时 findAllActive 会抛 no such table: proxy_account_binding。这里额外执行一次，保证所有表都建好。
     *
     * <p>按方言分发：sqlite 用原 schema-proxy.sql + proxy-bindings.sql（SQLite 方言），
     * mysql/postgres 用 schema-proxy-{dialect}.sql（对应方言，含 proxy_config + proxy_account_binding 等全表）。
     * 否则 MySQL 8 上 CREATE UNIQUE INDEX IF NOT EXISTS / INTEGER PRIMARY KEY / DATETIME 全炸。</p>
     */
    private void executeProxySchema() throws Exception {
        String dialect = databaseProvider != null ? databaseProvider.dialect() : "sqlite";
        if ("mysql".equalsIgnoreCase(dialect) || "postgres".equalsIgnoreCase(dialect)) {
            executeSchemaFile("db/schema-proxy-" + dialect.toLowerCase() + ".sql");
        } else {
            executeSchemaFile("db/schema-proxy.sql");
            executeSchemaFile("db/proxy-bindings.sql");
        }
    }

    private void executeSchemaFile(String schemaFile) throws Exception {
        try (java.sql.Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             BufferedReader br = new BufferedReader(new InputStreamReader(
                     new ClassPathResource(schemaFile).getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder cur = new StringBuilder();
            List<String> stmts = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = stripInlineComment(line);
                String stripped = line.trim();
                if (stripped.isEmpty()) continue;
                cur.append(line).append('\n');
                if (stripped.endsWith(";")) {
                    String s = cur.toString().trim();
                    if (!s.isEmpty()) stmts.add(s);
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) stmts.add(s);
            }

            int ok = 0, skip = 0;
            Exception firstHardError = null;
            for (String sql : stmts) {
                String[] indexParts = createIndexParts(sql);
                if (indexParts != null && indexExists(conn, indexParts[1], indexParts[0])) {
                    skip++;
                    logger.debug("schema index skipped (already exists): {}", indexParts[0]);
                    continue;
                }
                try {
                    st.execute(sql);
                    ok++;
                } catch (Exception ex) {
                    // 「表/索引已存在」是幂等建表（CREATE TABLE IF NOT EXISTS / CREATE INDEX）的正常情况，跳过；
                    // 其他错误（语法错、列冲突等）是真错误，必须冒泡让 init() 停下，否则后续
                    // ensureColumn 会对着空库 ALTER TABLE 抛 no such table，刷屏。
                    //
                    // MySQL 8 的 CREATE INDEX 不支持 IF NOT EXISTS，二次启动会抛
                    // "Duplicate key name 'xxx'"；PG 抛 "relation ... already exists"。
                    // SQLite 抛 "already exists"。把这些幂等文案都当 skip。
                    String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                    boolean alreadyExists = msg.contains("already exists")
                            || msg.contains("已存在")
                            || msg.contains("duplicate key name")
                            || msg.contains("duplicate entry")
                            || msg.contains("relation") && msg.contains("already exists");
                    if (alreadyExists) {
                        skip++;
                        logger.debug("schema stmt skipped (already exists): {}", sql.substring(0, Math.min(80, sql.length())));
                        continue;
                    }
                    // 老库升级期：schema 文件里「CREATE INDEX ON 新列」对还没 ALTER 补列的旧表会抛
                    // no such column / does not exist / unknown column。这是升级期正常情况——
                    // 后续 ensureColumn 会幂等补列，不该当硬错冒泡跳掉 ensure* 兜底链。
                    // 必须放 skip 而非冒泡，否则 ensureVirtualColumns() 会被整个跳过（现网症结）。
                    boolean newColumnNotYetAdded = msg.contains("no such column")
                            || msg.contains("no such column:")
                            || msg.contains("unknown column")
                            || msg.contains("does not exist")
                            || msg.contains("column") && (msg.contains("not found") || msg.contains("missing"));
                    if (newColumnNotYetAdded && sql.trim().toUpperCase().startsWith("CREATE INDEX")) {
                        skip++;
                        logger.debug("schema CREATE INDEX skipped (column not yet ALTER-ed, ensureColumn will backfill): {}",
                                sql.substring(0, Math.min(80, sql.length())));
                        continue;
                    }
                    if (firstHardError == null) firstHardError = ex;
                    logger.error("schema stmt FAILED (hard error): {} | sql: {}", ex.getMessage(),
                            sql.substring(0, Math.min(120, sql.length())));
                }
            }
            logger.info("Schema executed ({}): {} statements ok, {} skipped", schemaFile, ok, skip);
            if (firstHardError != null) {
                throw firstHardError;
            }
        }
    }

    private String stripInlineComment(String line) {
        boolean inSingle = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                inSingle = !inSingle;
            } else if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-' && !inSingle) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private String[] createIndexParts(String sql) {
        if (sql == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+[`\\\"]?([a-zA-Z0-9_]+)[`\\\"]?\\s+ON\\s+[`\\\"]?([a-zA-Z0-9_]+)[`\\\"]?\\s*\\(")
                .matcher(sql);
        if (!m.find()) return null;
        return new String[] { m.group(1), m.group(2) };
    }

    private void ensureProductColumns() {
        ensureColumn("xianyu_product", "image_url", "VARCHAR(512)");
        ensureColumn("xianyu_product", "raw_data", "TEXT");
        ensureColumn("xianyu_product", "auction_type", "VARCHAR(32)");
        ensureColumn("xianyu_product", "item_status_raw", "VARCHAR(32)");
        ensureColumn("xianyu_product", "post_info", "VARCHAR(64)");
        ensureColumn("xianyu_product", "image_infos", "TEXT");
        ensureColumn("xianyu_product", "pic_width", "INTEGER");
        ensureColumn("xianyu_product", "pic_height", "INTEGER");
        ensureColumn("xianyu_product", "has_video", "BOOLEAN");
        // 运费偏好列（NONE=无需邮寄/FREE=包邮/DISTANCE=按距离计费）：
        // 实体 XianyuProduct.shippingMode 映射，旧库升级需 ALTER 兜底，否则 MyBatis-Plus
        // 全字段 SELECT 报 Unknown column 'shipping_mode'
        ensureColumn("xianyu_product", "shipping_mode", "VARCHAR(16) DEFAULT 'NONE'");
    }

    private void ensureAiColumns() {
        ensureColumn("xianyu_auto_reply_config", "ai_model_id", "BIGINT");
        ensureColumn("ai_ops_task", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_suggestion", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureVirtualColumns() {
        ensureColumn("virtual_card_pool", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("virtual_card_pool", "used_order_id", "INTEGER");
        // virtual_ship_task 早期 schema 缺 account_id 等列，旧库 ALTER 补齐（幂等）
        ensureColumn("virtual_ship_task", "account_id", "BIGINT");
        ensureColumn("virtual_ship_task", "order_id", "BIGINT");
        ensureColumn("virtual_ship_task", "product_id", "BIGINT");
        ensureColumn("virtual_ship_task", "status", "VARCHAR(16) DEFAULT 'PENDING'");
        ensureColumn("virtual_ship_task", "retry_count", "INTEGER DEFAULT 0");
        ensureColumn("virtual_ship_task", "max_retry", "INTEGER DEFAULT 5");
        ensureColumn("virtual_ship_task", "error_message", "TEXT");
        ensureColumn("virtual_ship_task", "execute_at", "DATETIME");
        ensureColumn("virtual_ship_task", "processed_at", "DATETIME");
        // virtual_ship_task 发货送达回执链路新增列（幂等）：sent_at 在 sqlite/mysql 写 DATETIME、postgres 写 TIMESTAMP，
        // 现网 ensureColumn 第三参数 DDL 方言无关（PG 把 DATETIME 当 TIMESTAMP 兼容），照 processed_at 写法即可。
        ensureColumn("virtual_ship_task", "message_id", "VARCHAR(128)");
        ensureColumn("virtual_ship_task", "sent_at", "DATETIME");
        // virtual_ship_config 早期 schema 缺列，旧库 ALTER 补齐（幂等）
        ensureColumn("virtual_ship_config", "account_id", "BIGINT");
        ensureColumn("virtual_ship_config", "enabled", "INTEGER DEFAULT 0");
        ensureColumn("virtual_ship_config", "delay_seconds", "INTEGER DEFAULT 0");
        ensureColumn("virtual_ship_config", "auto_confirm_days", "INTEGER DEFAULT 7");
        ensureColumn("virtual_ship_config", "confirm_receipt_message", "TEXT");
        ensureColumn("virtual_ship_config", "notify_after_ship", "INTEGER DEFAULT 1");
    }

    private void ensureOrderColumns() {
        ensureColumn("xianyu_order", "type", "VARCHAR(16) DEFAULT 'BOUGHT'");
        ensureColumn("xianyu_order", "item_id", "VARCHAR(64)");
        ensureColumn("xianyu_order", "buyer_id", "VARCHAR(64)");
        ensureColumn("xianyu_order", "seller_id", "VARCHAR(64)");
        ensureColumn("xianyu_order", "order_detail_url", "VARCHAR(512)");
        ensureColumn("xianyu_order", "raw_data", "TEXT");
        ensureColumn("xianyu_order", "trade_status_enum", "VARCHAR(32)");
        ensureColumn("xianyu_order", "is_seller", "TINYINT(1)");
        ensureColumn("xianyu_order", "goods_type", "VARCHAR(16) DEFAULT 'PHYSICAL'");
        ensureColumn("xianyu_order", "product_id", "INTEGER");
        ensureColumn("xianyu_order", "require_virtual_ship", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_order", "virtual_shipped_at", "DATETIME");
        ensureColumn("xianyu_order", "auto_receipt_at", "DATETIME");
        ensureColumn("xianyu_order", "deliver_content", "TEXT");
        // BOT-O1 订单状态机后加的列，旧库可能缺失（ensureColumn 幂等，已有则跳过）
        ensureColumn("xianyu_order", "order_id", "VARCHAR(64)");
        ensureColumn("xianyu_order", "status", "VARCHAR(32) DEFAULT 'PENDING'");
        ensureColumn("xianyu_order", "amount", "REAL");
        ensureColumn("xianyu_order", "item_title", "VARCHAR(256)");
        ensureColumn("xianyu_order", "counterparty_name", "VARCHAR(128)");
        ensureColumn("xianyu_order", "order_time", "DATETIME");
        ensureColumn("xianyu_order", "tracking_no", "VARCHAR(64)");
        ensureColumn("xianyu_order", "order_status", "VARCHAR(32) DEFAULT 'CREATED'");
        ensureColumn("xianyu_order", "pre_refund_status", "VARCHAR(32)");
    }

    private void ensureMessageColumns() {
        ensureColumn("xianyu_message", "msg_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_name", "VARCHAR(128)");
        ensureColumn("xianyu_message", "sender_avatar", "VARCHAR(512)");
        ensureColumn("xianyu_message", "msg_type", "VARCHAR(16)");
        ensureColumn("xianyu_message", "direction", "VARCHAR(8)");
        ensureColumn("xianyu_message", "auto_reply", "BOOLEAN");
        // 旁路业务字段（cid + biz_order/item/buyer_id）老库升级兜底
        ensureColumn("xianyu_message", "cid", "VARCHAR(64)");
        ensureColumn("xianyu_message", "biz_order_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "biz_item_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "biz_buyer_id", "VARCHAR(64)");
    }

    private void ensureImColumns() {
        ensureColumn("xianyu_account", "im_cookie_header", "TEXT");
        ensureColumn("xianyu_account", "im_device_id", "VARCHAR(128)");
        ensureColumn("xianyu_account", "im_access_token", "TEXT");
        ensureColumn("xianyu_account", "im_token_expires_at", "DATETIME");
    }

    private void ensureRuleColumns() {
        ensureColumn("xianyu_keyword_rule", "action", "VARCHAR(16)");
        ensureColumn("xianyu_keyword_rule", "action_target_item_id", "VARCHAR(64)");
    }

    private void ensureAccountIdentityFromCookie() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (!tableExists(conn, "xianyu_account")) return;
            try (java.sql.PreparedStatement query = conn.prepareStatement(
                    "SELECT id, account_name, user_id, display_name, cookie_header FROM xianyu_account WHERE deleted = 0");
                 java.sql.ResultSet rs = query.executeQuery();
                 java.sql.PreparedStatement update = conn.prepareStatement(
                         "UPDATE xianyu_account SET user_id = ?, display_name = ? WHERE id = ?")) {
                int updated = 0;
                while (rs.next()) {
                    String userId = trim(rs.getString("user_id"));
                    String displayName = trim(rs.getString("display_name"));
                    String accountName = trim(rs.getString("account_name"));
                    String cookie = rs.getString("cookie_header");
                    String cookieUserId = firstNotBlank(cookieValue(cookie, "unb"), cookieValue(cookie, "userId"));
                    String resolvedUserId = firstNotBlank(userId, stripGoofishSuffix(cookieUserId));
                    String resolvedDisplayName = firstNotBlank(displayName, accountName, cookieValue(cookie, "tracknick"));
                    if (resolvedUserId.equals(userId) && resolvedDisplayName.equals(displayName)) continue;
                    update.setString(1, resolvedUserId);
                    update.setString(2, resolvedDisplayName);
                    update.setLong(3, rs.getLong("id"));
                    update.addBatch();
                    updated++;
                }
                if (updated > 0) {
                    update.executeBatch();
                    logger.info("Backfilled {} xianyu_account identity rows from cookie", updated);
                }
            }
        } catch (Exception e) {
            logger.warn("ensureAccountIdentityFromCookie skipped: {}", e.getMessage());
        }
    }

    private String cookieValue(String cookie, String name) {
        if (cookie == null || cookie.isBlank() || name == null || name.isBlank()) return "";
        String prefix = name + "=";
        for (String seg : cookie.split(";")) {
            String trimmed = seg.trim();
            if (trimmed.startsWith(prefix)) return trimmed.substring(prefix.length()).trim();
        }
        return "";
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isEmpty()) return trimmed;
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String stripGoofishSuffix(String userId) {
        String trimmed = trim(userId);
        int at = trimmed.indexOf('@');
        return at > 0 ? trimmed.substring(0, at) : trimmed;
    }

    /**
     * 补齐 xianyu_account 的会话/资料字段（旧库升级场景）。
     * <p>老库在 last_keepalive_at 之前建表，MyBatis-Plus 按实体全字段 SELECT 时会抛
     * "Unknown column 'last_keepalive_at' in 'field list'"，导致账号列表/保活任务全挂。
     * schema-*.sql 新建库已含这些列；此处用 ALTER 逐列兜底已有库，保证与
     * {@code XianyuAccount} 实体字段一一对应。</p>
     */
    private void ensureAccountColumns() {
        // 会话与 Cookie 生命周期
        ensureColumn("xianyu_account", "last_login_at", "DATETIME");
        ensureColumn("xianyu_account", "last_keepalive_at", "DATETIME");
        ensureColumn("xianyu_account", "cookie_expires_at", "DATETIME");
        ensureColumn("xianyu_account", "cookies_json", "TEXT");
        ensureColumn("xianyu_account", "last_error", "VARCHAR(512)");
        // 个人信息（闲鱼 API 拉取）
        ensureColumn("xianyu_account", "avatar", "VARCHAR(512)");
        ensureColumn("xianyu_account", "introduction", "TEXT");
        ensureColumn("xianyu_account", "ip_location", "VARCHAR(64)");
        ensureColumn("xianyu_account", "followers", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "following", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "sold_count", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "purchase_count", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "collection_count", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "on_sale_count", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "shop_level", "VARCHAR(32)");
        ensureColumn("xianyu_account", "credit_score", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "review_num", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "profile_synced_at", "DATETIME");
    }

    /**
     * 补齐 xianyu_account 的 Chrome 容器隔离字段（旧库升级场景）。
     * schema-sqlite.sql 新建库已含这些列；此处用 ALTER 兜底已有库。
     */
    private void ensureChromeColumns() {
        ensureColumn("xianyu_account", "chrome_profile_path", "VARCHAR(512)");
        ensureColumn("xianyu_account", "cdp_port", "INTEGER");
        ensureColumn("xianyu_account", "proxy_url", "VARCHAR(256)");
        ensureColumn("xianyu_account", "chrome_status", "VARCHAR(32)");
        ensureColumn("xianyu_account", "chrome_crash_count", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_account", "chrome_seed", "BIGINT");
        ensureColumn("xianyu_account", "chrome_launched_at", "DATETIME");
    }

    /**
     * 补齐 local_product 的 shipping_mode 列（旧库升级场景）。
     * schema-*.sql 新建库已含此列；此处用 ALTER 兜底已有库，
     * 与 LocalProduct.shippingMode 字段 + 导入链路 COL_SHIPPING_MODE 对齐。
     */
    private void ensureLocalProductShippingModeColumn() {
        ensureColumn("local_product", "shipping_mode", "VARCHAR(16) DEFAULT 'NONE'");
    }

    /**
     * 确保 market_keyword 表存在（已有数据库不会自动新建，按 ensureTable 模式补建）
     */
    private void ensureMarketKeywordTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "market_keyword")) {
                return;
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS market_keyword ("
                        + "id INTEGER PRIMARY KEY,"
                        + "keyword VARCHAR(256) UNIQUE NOT NULL,"
                        + "status VARCHAR(16) DEFAULT 'ACTIVE',"
                        + "crawl_interval_minutes INTEGER DEFAULT 30,"
                        + "last_crawl_at DATETIME,"
                        + "last_crawl_result_count INTEGER DEFAULT 0,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "deleted INTEGER DEFAULT 0"
                        + ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_market_keyword_status ON market_keyword(status)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_market_keyword_deleted ON market_keyword(deleted)");
                logger.info("Created missing table market_keyword");
            }
        } catch (Exception e) {
            logger.warn("ensureMarketKeywordTable skipped: {}", e.getMessage());
        }
    }

    private void ensureAutoReplyLogTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "xianyu_auto_reply_log")) {
                return;
            }
            String dialect = databaseProvider != null ? databaseProvider.dialect() : "sqlite";
            String idDdl;
            String timeDdl;
            String boolDdl;
            if ("mysql".equalsIgnoreCase(dialect)) {
                idDdl = "BIGINT AUTO_INCREMENT PRIMARY KEY";
                timeDdl = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                boolDdl = "TINYINT(1) DEFAULT 0";
            } else if ("postgres".equalsIgnoreCase(dialect)) {
                idDdl = "BIGSERIAL PRIMARY KEY";
                timeDdl = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                boolDdl = "BOOLEAN DEFAULT FALSE";
            } else {
                idDdl = "INTEGER PRIMARY KEY";
                timeDdl = "DATETIME DEFAULT CURRENT_TIMESTAMP";
                boolDdl = "BOOLEAN DEFAULT FALSE";
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS xianyu_auto_reply_log ("
                        + "id " + idDdl + ", "
                        + "account_id INTEGER, "
                        + "rule_id INTEGER, "
                        + "rule_name VARCHAR(128), "
                        + "reply_type VARCHAR(16), "
                        + "keyword VARCHAR(256), "
                        + "buyer_message TEXT, "
                        + "reply_text TEXT, "
                        + "matched " + boolDdl + ", "
                        + "created_at " + timeDdl
                        + ")");
                logger.info("Created missing table xianyu_auto_reply_log");
            }
        } catch (Exception e) {
            logger.warn("ensureAutoReplyLogTable skipped: {}", e.getMessage());
        }
    }

    private void ensureVipTables() {
        try (java.sql.Connection conn = dataSource.getConnection(); java.sql.Statement st = conn.createStatement()) {
            String d = dialect();
            String idPk = "postgres".equals(d) ? "BIGSERIAL PRIMARY KEY" : "mysql".equals(d) ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String timeDdl = "mysql".equals(d) || "postgres".equals(d) ? "TIMESTAMP" : "DATETIME";
            String boolDdl = "mysql".equals(d) ? "TINYINT(1)" : "BOOLEAN";
            st.execute("CREATE TABLE IF NOT EXISTS sdk_deployment ("
                    + "id " + idPk + ", deployment_id VARCHAR(128) NOT NULL UNIQUE, install_time " + timeDdl + ", server_url VARCHAR(512), "
                    + "app_id VARCHAR(128), app_secret VARCHAR(512), "
                    + "bound_email VARCHAR(191), email_verified " + boolDdl + " DEFAULT FALSE, email_verified_at " + timeDdl + ", community_uid VARCHAR(64), last_identity_sync_at " + timeDdl + ", access_expired_at BIGINT DEFAULT 0, "
                    + "created_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, updated_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, deleted INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS community_user_binding ("
                    + "id " + idPk + ", local_user_id BIGINT NOT NULL, deployment_id VARCHAR(128) NOT NULL, community_user_id BIGINT, community_uid VARCHAR(64), "
                    + "bind_id VARCHAR(128), bind_token VARCHAR(512), new_api_base_url VARCHAR(512), status VARCHAR(32), initial_pay_channel VARCHAR(32), initial_channel_prefix VARCHAR(16), "
                    + "wechat_bound " + boolDdl + " DEFAULT FALSE, email_bound " + boolDdl + " DEFAULT FALSE, email VARCHAR(191), email_verified " + boolDdl + " DEFAULT FALSE, email_verified_at " + timeDdl + ", identity_status VARCHAR(32), last_restore_at " + timeDdl + ", last_sync_at " + timeDdl + ", "
                    + "created_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, updated_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, deleted INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS vip_subscription ("
                    + "id " + idPk + ", local_user_id BIGINT NOT NULL, deployment_id VARCHAR(128) NOT NULL, community_user_id BIGINT, community_uid VARCHAR(64), "
                    + "license_id VARCHAR(128), email VARCHAR(191), plan_code VARCHAR(64), vip_level VARCHAR(32), features_json TEXT, limits_json TEXT, started_at " + timeDdl + ", expired_at " + timeDdl + ", "
                    + "status VARCHAR(32), source_order_no VARCHAR(128), signature TEXT, last_verified_at " + timeDdl + ", "
                    + "created_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, updated_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, deleted INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS vip_order ("
                    + "id " + idPk + ", local_user_id BIGINT NOT NULL, deployment_id VARCHAR(128) NOT NULL, community_user_id BIGINT, community_uid VARCHAR(64), "
                    + "local_order_no VARCHAR(128) NOT NULL UNIQUE, new_api_order_no VARCHAR(128), plan_id VARCHAR(64), plan_code VARCHAR(64), plan_name VARCHAR(128), pay_channel VARCHAR(32), "
                    + "pay_amount DECIMAL(12,2), currency VARCHAR(16), email VARCHAR(191), identity_verified " + boolDdl + " DEFAULT FALSE, status VARCHAR(32), pay_info_json TEXT, entitlement_json TEXT, paid_at " + timeDdl + ", "
                    + "created_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, updated_at " + timeDdl + " DEFAULT CURRENT_TIMESTAMP, deleted INTEGER DEFAULT 0)");
            logger.info("VIP tables base ensured");
        } catch (Exception e) {
            logger.warn("ensureVipTables base skipped: {}", e.getMessage());
            return;
        }

        String d = dialect();
        String timeDdl = "mysql".equals(d) || "postgres".equals(d) ? "TIMESTAMP" : "DATETIME";
        String boolDdl = "mysql".equals(d) ? "TINYINT(1)" : "BOOLEAN";
        ensureColumn("sdk_deployment", "bound_email", "VARCHAR(191)");
        ensureColumn("sdk_deployment", "email_verified", boolDdl + " DEFAULT FALSE");
        ensureColumn("sdk_deployment", "email_verified_at", timeDdl);
        ensureColumn("sdk_deployment", "community_uid", "VARCHAR(64)");
        ensureColumn("sdk_deployment", "last_identity_sync_at", timeDdl);
        ensureColumn("sdk_deployment", "access_expired_at", "BIGINT DEFAULT 0");
        ensureColumn("sdk_deployment", "app_id", "VARCHAR(128)");
        ensureColumn("sdk_deployment", "app_secret", "VARCHAR(512)");
        ensureColumn("community_user_binding", "email", "VARCHAR(191)");
        ensureColumn("community_user_binding", "email_verified", boolDdl + " DEFAULT FALSE");
        ensureColumn("community_user_binding", "email_verified_at", timeDdl);
        ensureColumn("community_user_binding", "identity_status", "VARCHAR(32)");
        ensureColumn("community_user_binding", "last_restore_at", timeDdl);
        ensureColumn("vip_subscription", "email", "VARCHAR(191)");
        ensureColumn("vip_order", "email", "VARCHAR(191)");
        ensureColumn("vip_order", "identity_verified", boolDdl + " DEFAULT FALSE");

        try (java.sql.Connection conn = dataSource.getConnection(); java.sql.Statement st = conn.createStatement()) {
            ensureIndex(conn, st, "community_user_binding", "idx_community_user_binding_local", "CREATE INDEX idx_community_user_binding_local ON community_user_binding(local_user_id, deployment_id)");
            ensureIndex(conn, st, "vip_subscription", "idx_vip_subscription_local", "CREATE INDEX idx_vip_subscription_local ON vip_subscription(local_user_id, deployment_id)");
            ensureIndex(conn, st, "vip_order", "idx_vip_order_local", "CREATE INDEX idx_vip_order_local ON vip_order(local_user_id, local_order_no)");
            logger.info("VIP tables ensured");
        } catch (Exception e) {
            logger.warn("ensureVipTables indexes skipped: {}", e.getMessage());
        }
    }

    private void ensureOpenAppTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (tableExists(conn, "open_app")) {
                return;
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute(createOpenAppSql());
                st.execute("CREATE INDEX idx_open_app_key ON open_app(app_key)");
                logger.info("Created missing table open_app and index idx_open_app_key (dialect={})", dialect());
            }
        } catch (Exception e) {
            logger.warn("ensureOpenAppTable skipped: {}", e.getMessage());
        }
    }

    /** open_app 建表 SQL（按方言分发） */
    private String createOpenAppSql() {
        String d = dialect();
        switch (d) {
            case "mysql":
                return "CREATE TABLE open_app ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "app_name VARCHAR(128) NOT NULL, "
                        + "app_key VARCHAR(64) NOT NULL UNIQUE, "
                        + "app_secret_enc VARCHAR(512), "
                        + "status VARCHAR(16) DEFAULT 'ENABLED', "
                        + "bound_account_ids TEXT, "
                        + "rate_limit_per_minute INTEGER DEFAULT 60, "
                        + "expire_at TIMESTAMP NULL, "
                        + "last_used_at TIMESTAMP NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "deleted INTEGER DEFAULT 0"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            case "postgres":
                return "CREATE TABLE open_app ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "app_name VARCHAR(128) NOT NULL, "
                        + "app_key VARCHAR(64) NOT NULL UNIQUE, "
                        + "app_secret_enc VARCHAR(512), "
                        + "status VARCHAR(16) DEFAULT 'ENABLED', "
                        + "bound_account_ids TEXT, "
                        + "rate_limit_per_minute INTEGER DEFAULT 60, "
                        + "expire_at TIMESTAMP, "
                        + "last_used_at TIMESTAMP, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "deleted INTEGER DEFAULT 0"
                        + ")";
            default:
                return "CREATE TABLE open_app ("
                        + "id INTEGER PRIMARY KEY, "
                        + "app_name VARCHAR(128) NOT NULL, "
                        + "app_key VARCHAR(64) NOT NULL UNIQUE, "
                        + "app_secret_enc VARCHAR(512), "
                        + "status VARCHAR(16) DEFAULT 'ENABLED', "
                        + "bound_account_ids TEXT, "
                        + "rate_limit_per_minute INTEGER DEFAULT 60, "
                        + "expire_at DATETIME, "
                        + "last_used_at DATETIME, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "deleted INTEGER DEFAULT 0"
                        + ")";
        }
    }

    // ======================== 新增模块迁移 ========================

    private void ensureMarketColumns() {
        // market_snapshot 表补齐（继承 BaseEntity，旧库/当前 schema 可能缺 updated_at/deleted）
        ensureColumn("market_snapshot", "raw_data", "TEXT");
        // 扩容 raw_data：MySQL TEXT 上限 65KB，改为 MEDIUMTEXT（16MB）避免大数据截断
        tryExpandColumnType("market_snapshot", "raw_data", "MEDIUMTEXT");
        ensureColumn("market_snapshot", "total_results", "INTEGER DEFAULT 0");
        ensureColumn("market_snapshot", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("market_snapshot", "deleted", "INTEGER DEFAULT 0");
        // task_id 允许为 NULL：市场情报抓取不关联监控任务，旧库 NOT NULL 会导致插入报
        // "Field 'task_id' doesn't have a default value"（MySQL 严格模式），启动时幂等迁移。
        makeColumnNullable("market_snapshot", "task_id");
        // price_history 表补齐（继承 BaseEntity，旧库/当前 schema 可能缺 updated_at/deleted）
        ensureColumn("price_history", "currency", "VARCHAR(8) DEFAULT 'CNY'");
        ensureColumn("price_history", "item_condition", "VARCHAR(32)");
        ensureColumn("price_history", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("price_history", "deleted", "INTEGER DEFAULT 0");
        // market_daily_stat 表补齐（继承 BaseEntity，旧库/当前 schema 可能缺 deleted）
        ensureColumn("market_daily_stat", "p25_price", "REAL");
        ensureColumn("market_daily_stat", "p75_price", "REAL");
        ensureColumn("market_daily_stat", "volume", "INTEGER DEFAULT 0");
        ensureColumn("market_daily_stat", "sampled_count", "INTEGER DEFAULT 0");
        ensureColumn("market_daily_stat", "deleted", "INTEGER DEFAULT 0");
    }

    private void ensureMonitorColumns() {
        // monitor_task 表补齐
        ensureColumn("monitor_task", "ai_prompt", "TEXT");
        ensureColumn("monitor_task", "ai_model_id", "BIGINT");
        ensureColumn("monitor_task", "cron_expression", "VARCHAR(64)");
        ensureColumn("monitor_task", "interval_minutes", "INTEGER DEFAULT 30");
        ensureColumn("monitor_task", "circuit_open", "INTEGER DEFAULT 0");
        ensureColumn("monitor_task", "circuit_open_until", "DATETIME");
        // monitor_result 表补齐
        ensureColumn("monitor_result", "matched_keywords", "TEXT");
        ensureColumn("monitor_result", "ai_score", "REAL");
        ensureColumn("monitor_result", "ai_reason", "TEXT");
    }

    private void ensureBuyerProfileColumns() {
        // buyer_profile 表补齐
        ensureColumn("buyer_profile", "credibility_score", "REAL DEFAULT 50");
        ensureColumn("buyer_profile", "tags", "TEXT");
        ensureColumn("buyer_profile", "notes", "TEXT");
        ensureColumn("buyer_profile", "total_spent", "REAL DEFAULT 0");
        // ai_cs_session_state 表补齐（继承 BaseEntity，旧库 schema 可能缺 deleted/created_at/updated_at）
        ensureColumn("ai_cs_session_state", "lowest_offer", "REAL");
        ensureColumn("ai_cs_session_state", "current_offer", "REAL");
        ensureColumn("ai_cs_session_state", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_cs_session_state", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_cs_session_state", "deleted", "INTEGER DEFAULT 0");
    }

    private void ensureCircuitBreakerColumns() {
        // circuit_breaker 表补齐
        ensureColumn("circuit_breaker", "half_open_max_success", "INTEGER DEFAULT 3");
        ensureColumn("circuit_breaker", "cooldown_seconds", "INTEGER DEFAULT 300");
        ensureColumn("circuit_breaker", "threshold_count", "INTEGER DEFAULT 5");
        ensureColumn("circuit_breaker", "last_failure_message", "TEXT");
    }

    private void ensureAdminUserColumns() {
        // admin_user 早期 schema 可能缺列，旧库 ALTER 补齐（幂等）
        ensureColumn("admin_user", "display_name", "VARCHAR(128)");
        ensureColumn("admin_user", "email", "VARCHAR(128)");
        ensureColumn("admin_user", "phone", "VARCHAR(32)");
        ensureColumn("admin_user", "role_level", "INTEGER DEFAULT 1");
    }

    private void ensureAiCsSessionStateColumns() {
        // ai_cs_session 表补齐
        ensureColumn("ai_cs_session", "product_id", "INTEGER");
        ensureColumn("ai_cs_session", "order_id", "INTEGER");
    }

    /**
     * B9 批次日志框架：旧库启动时若 batch_job / batch_job_item 表缺失则按当前 dialect 建表兜底。
     * 新库已通过 schema*.sql 建好，这里只针对升级场景。
     */
    private void ensureBatchTables() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (!tableExists(conn, "batch_job")) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute(buildBatchJobDdl());
                    logger.info("Created missing table batch_job (B9 framework)");
                }
            }
            if (!tableExists(conn, "batch_job_item")) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute(buildBatchJobItemDdl());
                    logger.info("Created missing table batch_job_item (B9 framework)");
                }
            }
        } catch (Exception e) {
            logger.debug("ensureBatchTables skipped: {}", e.getMessage());
        }
    }

    /** 按 dialect 生成 batch_job 建表 SQL。 */
    private String buildBatchJobDdl() {
        String idType = databaseProvider != null && "postgres".equals(databaseProvider.dialect())
                ? "BIGSERIAL" : "INTEGER";
        String idPk = "postgres".equals(databaseProvider == null ? "sqlite" : databaseProvider.dialect())
                      || "mysql".equals(databaseProvider == null ? "sqlite" : databaseProvider.dialect())
                ? idType + " PRIMARY KEY AUTO_INCREMENT"
                : idType + " PRIMARY KEY AUTOINCREMENT";
        return "CREATE TABLE batch_job ("
                + "id " + idPk + ", "
                + "job_type VARCHAR(64) NOT NULL, "
                + "job_code VARCHAR(128), "
                + "trigger_source VARCHAR(16) DEFAULT 'SCHEDULER', "
                + "status VARCHAR(16) DEFAULT 'RUNNING', "
                + "total_count INTEGER DEFAULT 0, "
                + "success_count INTEGER DEFAULT 0, "
                + "failed_count INTEGER DEFAULT 0, "
                + "skipped_count INTEGER DEFAULT 0, "
                + "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "ended_at TIMESTAMP, "
                + "summary VARCHAR(512), "
                + "failure_summary VARCHAR(2000), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "deleted INTEGER DEFAULT 0"
                + ")";
    }

    /** 按 dialect 生成 batch_job_item 建表 SQL。 */
    private String buildBatchJobItemDdl() {
        String idType = databaseProvider != null && "postgres".equals(databaseProvider.dialect())
                ? "BIGSERIAL" : "INTEGER";
        String idPk = "postgres".equals(databaseProvider == null ? "sqlite" : databaseProvider.dialect())
                      || "mysql".equals(databaseProvider == null ? "sqlite" : databaseProvider.dialect())
                ? idType + " PRIMARY KEY AUTO_INCREMENT"
                : idType + " PRIMARY KEY AUTOINCREMENT";
        return "CREATE TABLE batch_job_item ("
                + "id " + idPk + ", "
                + "batch_id BIGINT NOT NULL, "
                + "item_key VARCHAR(128), "
                + "item_label VARCHAR(256), "
                + "status VARCHAR(16), "
                + "duration_ms BIGINT, "
                + "failure_reason VARCHAR(512), "
                + "detail TEXT, "
                + "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "ended_at TIMESTAMP, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "deleted INTEGER DEFAULT 0"
                + ")";
    }

    private void ensureIndex(java.sql.Connection conn, java.sql.Statement st, String table, String indexName, String ddl) throws Exception {
        if (!indexExists(conn, table, indexName)) {
            st.execute(ddl);
        }
    }

    private void ensureColumn(String table, String column, String ddl) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            // 先查表是否存在；主 schema 没执行成功时空库对着不存在的表 ALTER TABLE 会炸，
            // 这里提前 return，避免刷屏 no such table。
            if (!tableExists(conn, table)) {
                logger.debug("ensureColumn {} on {}: table not exists, skip (schema may have failed)", column, table);
                return;
            }
            if (columnExists(conn, table, column)) {
                return;
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
                logger.info("Added column {} to {}", column, table);
            }
        } catch (Exception e) {
            logger.debug("ensureColumn {} on {}: {}", column, table, e.getMessage());
        }
    }

    /**
     * 扩容已有列的类型（仅对 MySQL 生效，其他数据库跳过）。
     * 用于把 TEXT 升级到 MEDIUMTEXT，避免大数据截断。
     */
    private void tryExpandColumnType(String table, String column, String newType) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (!tableExists(conn, table) || !columnExists(conn, table, column)) return;
            String dialect = dialect();
            if (!"mysql".equals(dialect)) return;
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + newType);
                logger.info("Expanded column {}.{}` to {}", table, column, newType);
            }
        } catch (Exception e) {
            logger.debug("tryExpandColumnType {}.{}` to {}: {}", table, column, newType, e.getMessage());
        }
    }

    /**
     * 去掉列上的 NOT NULL 约束（方言感知：mysql/postgres 支持 ALTER；sqlite 旧库无法 ALTER，
     * 跳过——新库由 schema*.sql 建表时已不声明 NOT NULL）。
     */
    private void makeColumnNullable(String table, String column) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (!tableExists(conn, table) || !columnExists(conn, table, column)) return;
            String d = dialect();
            try (java.sql.Statement st = conn.createStatement()) {
                if ("mysql".equals(d)) {
                    st.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " BIGINT NULL");
                    logger.info("Made column {}.{} nullable (mysql)", table, column);
                } else if ("postgres".equals(d)) {
                    st.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL");
                    logger.info("Made column {}.{} nullable (postgres)", table, column);
                } else {
                    logger.debug("makeColumnNullable {}: sqlite 不支持 ALTER 去 NOT NULL，跳过", table);
                }
            }
        } catch (Exception e) {
            logger.debug("makeColumnNullable {}.{}: {}", table, column, e.getMessage());
        }
    }

    // tableExists / columnExists 已迁移到类顶部 dialect-aware 实现（information_schema / sqlite_master 分发）
}
