package com.myblog.publicsite.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 生产 CORS（#5 9. 安全与配置、#16）：只接受 {@code SITE_ORIGIN} 指定的准确来源，
 * 关闭凭据。公开路径只允许 GET / HEAD / OPTIONS；Admin API 额外允许管理
 * 方法与 {@code Authorization} 头（会话令牌）。未配置 {@code SITE_ORIGIN}
 * 时关闭跨域放行（fail closed）。本地开发来源只存在于开发配置（application-dev.yml）。
 */
@Configuration
public class PublicCorsConfig implements WebMvcConfigurer {

    private final String siteOrigin;

    public PublicCorsConfig(@Value("${site.origin:}") String siteOrigin) {
        this.siteOrigin = siteOrigin == null ? "" : siteOrigin.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (siteOrigin.isEmpty()) {
            return;
        }
        // Admin API 映射必须先注册：UrlBasedCorsConfigurationSource 按注册顺序取第一个匹配。
        registry.addMapping("/api/admin/**")
                .allowedOrigins(siteOrigin)
                .allowedMethods("GET", "HEAD", "OPTIONS", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Accept", "Authorization")
                .maxAge(3600);
        registry.addMapping("/api/**")
                .allowedOrigins(siteOrigin)
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .maxAge(3600);
    }
}
