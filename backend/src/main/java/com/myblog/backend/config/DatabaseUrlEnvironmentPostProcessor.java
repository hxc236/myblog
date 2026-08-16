package com.myblog.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * 把部署环境常用的 {@code DATABASE_URL} 映射为 Spring 数据源属性（#15）。
 *
 * <p>Neon 提供的是 {@code postgres://user:pass@host:port/db?sslmode=require}
 * 形式的连接串（非 JDBC 前缀），本处理器在数据源自动配置之前把它转换为
 * {@code jdbc:postgresql://...} 并拆出用户名与密码。规则：
 *
 * <ul>
 *   <li>已显式配置 {@code spring.datasource.url} 时不改写（用户配置优先）；</li>
 *   <li>{@code DATABASE_URL} 已是 {@code jdbc:...} 形式时原样使用；</li>
 *   <li>{@code postgres://} 或 {@code postgresql://} 形式转换为 JDBC URL，</li>
 *   <li>未设置 {@code DATABASE_URL} 时不做任何事（公开站点按 MVP 文件读路径回退）。</li>
 * </ul>
 */
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String DATABASE_URL_PROPERTY = "DATABASE_URL";
    public static final String SPRING_DATASOURCE_URL = "spring.datasource.url";
    public static final String SPRING_DATASOURCE_USERNAME = "spring.datasource.username";
    public static final String SPRING_DATASOURCE_PASSWORD = "spring.datasource.password";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (hasText(environment.getProperty(SPRING_DATASOURCE_URL))) {
            return;
        }
        String databaseUrl = environment.getProperty(DATABASE_URL_PROPERTY);
        if (!hasText(databaseUrl)) {
            return;
        }
        Map<String, Object> mapped = map(databaseUrl.trim());
        environment.getPropertySources().addFirst(
                new MapPropertySource("databaseUrlEnvironmentPostProcessor", mapped));
    }

    static Map<String, Object> map(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return Map.of(SPRING_DATASOURCE_URL, databaseUrl);
        }
        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            throw new IllegalStateException(
                    "DATABASE_URL 必须是 jdbc:postgresql:// 或 postgres:// / postgresql:// 形式："
                            + databaseUrl);
        }
        try {
            URI uri = new URI(databaseUrl);
            if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalStateException("DATABASE_URL 缺少主机或数据库名：" + databaseUrl);
            }
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getRawQuery();
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
            if (port != -1) {
                jdbc.append(':').append(port);
            }
            jdbc.append(path);
            if (hasText(query)) {
                jdbc.append('?').append(query);
            }
            Map<String, Object> result = new HashMap<>();
            result.put(SPRING_DATASOURCE_URL, jdbc.toString());
            String userInfo = uri.getRawUserInfo();
            if (hasText(userInfo)) {
                int separator = userInfo.indexOf(':');
                if (separator >= 0) {
                    result.put(SPRING_DATASOURCE_USERNAME, userInfo.substring(0, separator));
                    result.put(SPRING_DATASOURCE_PASSWORD, userInfo.substring(separator + 1));
                } else {
                    result.put(SPRING_DATASOURCE_USERNAME, userInfo);
                }
            }
            return result;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("无法解析 DATABASE_URL：" + databaseUrl, e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}