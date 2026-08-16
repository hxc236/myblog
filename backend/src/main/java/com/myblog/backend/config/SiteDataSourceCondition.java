package com.myblog.backend.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 数据源读路径条件（#15）：仅在显式配置了非空的 {@code spring.datasource.url}
 * 时创建 PostgreSQL 数据源。
 *
 * <p>未配置时公开站点按 MVP 文件读路径回退启动，正式领域 API 返回 503。
 */
public class SiteDataSourceCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("spring.datasource.url");
        return url != null && !url.trim().isEmpty();
    }
}