package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置
 */
@Configuration
@EnableTransactionManagement
public class MybatisPlusConfig {

    /**
     * 分页插件按当前数据库方言动态设置 DbType，
     * 避免 sqlite/mysql/postgres 三套部署共用硬编码 SQLite 方言导致分页语法不匹配。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DatabaseProvider databaseProvider) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(toDbType(databaseProvider.dialect())));
        return interceptor;
    }

    private DbType toDbType(String dialect) {
        if (dialect == null) return DbType.SQLITE;
        return switch (dialect.toLowerCase()) {
            case "mysql" -> DbType.MYSQL;
            case "postgres", "postgresql", "pg" -> DbType.POSTGRE_SQL;
            case "sqlite" -> DbType.SQLITE;
            default -> DbType.SQLITE;
        };
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
