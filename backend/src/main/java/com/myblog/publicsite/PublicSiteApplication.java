package com.myblog.publicsite;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

/**
 * 隔离的公开站点应用（#5「后端运行时边界」）。
 *
 * <p>只扫描 {@code com.myblog.publicsite} 下的组件，并排除旧账户体系与
 * MyBatis-Plus 自动配置：不加载旧账户控制器、JWT 过滤器、legacy Mapper 或
 * 依赖 MySQL 的服务。
 *
 * <p>从 #15 起启用条件数据源读路径：配置了 {@code spring.datasource.url}
 * （或 {@code DATABASE_URL}，由 {@code DatabaseUrlEnvironmentPostProcessor}
 * 转换）时，{@code SiteDataSourceConfig} 创建 PostgreSQL 数据源，JdbcTemplate、
 * 事务管理器与 Flyway 自动配置随之生效，在空库上建立结构并写入初始值；
 * 未配置数据源时公开站点仍可按 MVP 文件读路径启动（短期回退快照），正式
 * 领域 API 返回 503 而不是退化为文件读取。
 */
@SpringBootApplication(
        scanBasePackages = "com.myblog.publicsite",
        exclude = {
                DataSourceAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        })
public class PublicSiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublicSiteApplication.class, args);
    }
}
