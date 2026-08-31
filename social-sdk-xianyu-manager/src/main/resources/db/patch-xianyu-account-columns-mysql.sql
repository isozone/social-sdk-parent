-- =====================================================================
-- 热修：xianyu_account 旧库缺列补丁（MySQL 8）
--
-- 现象：AccountMapper.selectList 抛
--   java.sql.SQLSyntaxErrorException: Unknown column 'last_keepalive_at' in 'field list'
--
-- 原因：库在 last_keepalive_at / cookie_expires_at / 个人资料等列加入 schema
-- 之前就已建表；schema-mysql.sql 用的是 CREATE TABLE IF NOT EXISTS，
-- 对已存在的表不会补列，而 MyBatis-Plus 会按实体全字段 SELECT。
--
-- 用法（在目标库执行一次即可，可重复执行）：
--   mysql -h<host> -u<user> -p<pass> <db> < patch-xianyu-account-columns-mysql.sql
--
-- 说明：
--   1) 脚本用 information_schema 判断列是否存在，不存在才 ALTER，可安全重跑；
--   2) 应用侧已内置同样逻辑（DatabaseInitializer#ensureAccountColumns），
--      重启后会自动补列；本脚本只用于「不想立刻重启」的线上热修；
--   3) 类型与 schema-mysql.sql 保持一致。
-- =====================================================================

SET @TABLE_NAME = 'xianyu_account';

-- 会话与 Cookie 生命周期
SET @COL = 'last_login_at';      SET @DDL = 'DATETIME';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'last_keepalive_at';  SET @DDL = 'DATETIME';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'cookie_expires_at';  SET @DDL = 'DATETIME';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'cookies_json';       SET @DDL = 'TEXT';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'last_error';         SET @DDL = 'VARCHAR(512)';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 个人信息（闲鱼 API 拉取）
SET @COL = 'avatar';             SET @DDL = 'VARCHAR(512)';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'introduction';       SET @DDL = 'TEXT';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'ip_location';        SET @DDL = 'VARCHAR(64)';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'followers';          SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'following';          SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'sold_count';         SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'purchase_count';     SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'collection_count';   SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'on_sale_count';      SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'shop_level';         SET @DDL = 'VARCHAR(32)';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'credit_score';       SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'review_num';         SET @DDL = 'INTEGER DEFAULT 0';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @COL = 'profile_synced_at';  SET @DDL = 'DATETIME';
SET @SQL = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @TABLE_NAME AND COLUMN_NAME = @COL) = 0,
              CONCAT('ALTER TABLE ', @TABLE_NAME, ' ADD COLUMN ', @COL, ' ', @DDL), 'SELECT 1');
PREPARE stmt FROM @SQL; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验：应能看到上面全部列
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'xianyu_account'
ORDER BY ORDINAL_POSITION;
