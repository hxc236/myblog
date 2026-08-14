package com.myblog.publicsite.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 条件数据源配置（#15）：配置了 {@code spring.datasource.url} 时创建
 * PostgreSQL 连接池，并让 JdbcTemplate、事务管理器与 Flyway 自动配置随之
 * 生效（Flyway 在空库上执行迁移并写入初始值）。
 *
 * <p>注意 {@code DataSourceAutoConfiguration} 在公开站点应用中被排除：它
 * 在缺少 URL 时会无条件失败，而本配置允许“未配置数据库时按 MVP 文件读
 * 路径回退启动”。
 */
@Configuration
public class SiteDataSourceConfig {

    @Bean
    @Conditional(SiteDataSourceCondition.class)
    public DataSource siteDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("site-postgres");
        dataSource.setJdbcUrl(url.trim());
        if (StringUtils.hasText(username)) {
            dataSource.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            dataSource.setPassword(password);
        }
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }
}
