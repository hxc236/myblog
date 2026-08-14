package com.myblog.publicsite;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

/**
 * 隔离的公开站点应用（#5「后端运行时边界」）。
 *
 * <p>只扫描 {@code com.myblog.publicsite} 下的组件，并排除数据源与安全自动配置：
 * 不加载旧账户控制器、JWT 过滤器、Mapper 或依赖数据库的服务；生产进程在
 * 没有 MySQL / 嵌入式数据库的情况下启动。
 */
@SpringBootApplication(
        scanBasePackages = "com.myblog.publicsite",
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
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
