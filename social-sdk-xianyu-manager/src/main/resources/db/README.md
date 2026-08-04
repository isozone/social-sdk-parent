# 数据库三选一配置指南

本平台支持 **SQLite / MySQL8 / PostgreSQL** 三种数据库，**三选一**（不并存），默认 SQLite。

## 切换方式

数据库类型只由显式配置 `bitefu.wall.db-type` 判断；`spring.profiles.active` 只负责加载对应配置文件，不能作为数据库类型判断标准。

| db-type | 可选配置文件 | schema 文件 | 适用场景 |
|---|---|---|---|
| `sqlite`（默认） | `application-sqlite.yml` | `db/schema-sqlite.sql` | 单机/开发/小规模 |
| `mysql` | `application-mysql.yml` | `db/schema-mysql.sql` | 中大规模生产 |
| `postgres` | `application-postgres.yml` | `db/schema-postgres.sql` | 中大规模生产 |

## 启动参数

### SQLite（默认）
```bash
java -jar xianyu-manager.jar
# 或显式：
java -jar xianyu-manager.jar --spring.profiles.active=sqlite
```

### MySQL8
```bash
# 先用 Docker 部署 MySQL8
docker run -d --name xianyu-mysql \
  -e MYSQL_ROOT_PASSWORD=xianyu123 \
  -e MYSQL_DATABASE=xianyu_manager \
  -p 3306:3306 mysql:8

# 启动应用
java -jar xianyu-manager.jar \
  --spring.profiles.active=mysql \
  --DB_URL=jdbc:mysql://localhost:3306/xianyu_manager?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true \
  --DB_USERNAME=root \
  --DB_PASSWORD=xianyu123
```

### PostgreSQL
```bash
# 先用 Docker 部署 PostgreSQL
docker run -d --name xianyu-pg \
  -e POSTGRES_USER=xianyu \
  -e POSTGRES_PASSWORD=xianyu123 \
  -e POSTGRES_DB=xianyu_manager \
  -p 5432:5432 postgres:16

# 启动应用
java -jar xianyu-manager.jar \
  --spring.profiles.active=postgres \
  --DB_URL=jdbc:postgresql://localhost:5432/xianyu_manager \
  --DB_USERNAME=xianyu \
  --DB_PASSWORD=xianyu123
```

## 环境变量

默认 SQLite 不读取 `DB_URL`，避免本机常驻 MySQL/PG 环境变量污染默认启动；只用 `DB_PATH` 指定本地库文件。

| 变量 | 默认值（SQLite） | 作用 |
|---|---|---|
| `DB_PATH` | `./data/xianyu-manager.db` | SQLite 数据库文件路径 |
| `DB_URL` | 仅 MySQL/PostgreSQL 配置文件使用 | JDBC URL |
| `DB_USERNAME` | 仅 MySQL/PostgreSQL 配置文件使用 | 用户名 |
| `DB_PASSWORD` | 仅 MySQL/PostgreSQL 配置文件使用 | 密码 |

> MySQL/PostgreSQL 启动时需加载对应配置文件，并由该文件显式设置 `bitefu.wall.db-type`。

## 内部实现

- **`DatabaseProvider` 接口**（`config/db/`）：方言抽象，定义 `schemaFile()` / `connectionInitSqls()` / `maxActive()` / `validationQuery()` 等。
- **三实现**：`SqliteProvider` / `MysqlProvider` / `PostgresProvider` 均通过 `@ConditionalOnProperty(prefix="bitefu.wall", name="db-type")` 激活。
- **`DruidConfig`**：从 `DatabaseProvider` 拿连接初始化 SQL 和 maxActive，SQLite WAL 模式 2 连接，MySQL/PG 并发 20。
- **`DatabaseInitializer`**：按当前 `DatabaseProvider.schemaFile()` 加载对应 schema 文件，启动时自动建表。
- **三套 schema**：方言差异已处理（SQLite `INTEGER PRIMARY KEY` / MySQL `BIGINT AUTO_INCREMENT` / PG `BIGSERIAL`；`DATETIME` → `TIMESTAMP`；外键引用列类型对齐）。

## 已实测

- SQLite：默认 schema 加载通过。
- MySQL8：Docker `mysql:8` 容器，schema-mysql.sql 全表建成（45 张表）。
- PostgreSQL：Docker `postgres:16` 容器，schema-postgres.sql 全表建成（44 张表）。
