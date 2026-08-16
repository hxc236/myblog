package com.myblog.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

/**
 * Personal Site 生产后端入口（#31 统一包根）。
 *
 * <p>生产代码唯一的包根是 {@code com.myblog.backend}，按 config、utils、
 * controller、service、service/impl、mapper 与 pojo 的职责组织；已废弃的
 * JWT、MySQL、MyBatis-Plus 账户、Plan 与 Todo 应用不再存在于构建中。
 *
 * <p>从 #15 起启用条件数据源读路径：配置了 {@code spring.datasource.url}
 * （或 {@code DATABASE_URL}，由 {@code DatabaseUrlEnvironmentPostProcessor}
 * 转换）时，{@code SiteDataSourceConfig} 创建 PostgreSQL 数据源，JdbcTemplate、
 * 事务管理器与 Flyway 自动配置随之生效，在空库上建立结构并写入初始值；
 * #30 起 MVP 文件读路径（{@code /api/v1}）已退出，PostgreSQL 是唯一运行时
 * 内容权威源；未配置数据源时公开领域 API 返回 503、管理端 fail closed。
 *
 * <p>从 #16 起由 {@code AdminSecurityConfig} 提供安全过滤链（GitHub OAuth +
 * 不透明会话令牌），因此不再排除 SecurityAutoConfiguration 与
 * SecurityFilterAutoConfiguration；仍排除 UserDetailsServiceAutoConfiguration，
 * 避免生成默认 user/密码账户。
 */
@SpringBootApplication(
        scanBasePackages = "com.myblog.backend",
        exclude = {
                DataSourceAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        })
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
