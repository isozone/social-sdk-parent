package cn.net.rjnetwork.xianyu.manager.config.migration;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * schema 版本记录 —— I7 数据迁移框架的「版本号 + 命名空间」表。
 * <p>每个迁移步骤（{@link MigrationStep}）执行成功后写一行，
 * 启动时按 namespace+version 去重，已执行过的步骤不再跑，保证迁移幂等。</p>
 *
 * <p>对标参考项目 db migrations 的思路，但更轻量：
 * 不引入 Flyway/Liquibase（外部依赖重），用本表 + DatabaseInitializer.startup hook 即可。</p>
 *
 * <p>命名空间示例：account / card / ship / batch / message …；
 * 版本号建议格式 yyyyMMdd 或递增整数（如 20260725_01）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schema_migration")
public class SchemaMigration extends BaseEntity {

    /** 命名空间，如 account / card / ship，避免不同模块版本号冲突 */
    private String namespace;
    /** 版本号，如 20260725_01；同一 namespace 下唯一 */
    private String version;
    /** 迁移描述，如「add cookie_refresh_schedule table」 */
    private String description;
    /** 执行耗时（毫秒） */
    private Long durationMs;
    /** 执行结果：SUCCESS / SKIPPED（已存在）/ FAILED */
    private String status;
    /** 失败原因（status=FAILED 时填） */
    private String failureReason;
    /** 执行时间 */
    private LocalDateTime executedAt;
}
