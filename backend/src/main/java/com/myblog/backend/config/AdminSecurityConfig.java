package com.myblog.backend.config;

import com.myblog.backend.config.AdminOAuthSuccessHandler;
import com.myblog.backend.config.AdminTokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

/**
 * Admin 安全配置（#16）：GitHub OAuth 登录 + 不透明会话令牌。
 *
 * <ul>
 *   <li>公开路径（{@code /api/v1/**}、{@code /api/site/**} 等）匿名可读；</li>
 *   <li>{@code /api/admin/**} 只接受通过 {@link AdminTokenAuthenticationFilter}
 *       校验的不透明会话令牌（不存在接受 legacy JWT 的机制）；</li>
 *   <li>OAuth 回调成功后必须命中唯一 allowlist（见
 *       {@link AdminOAuthSuccessHandler}）；</li>
 *   <li>未授权访问返回 401 JSON，不重定向登录页、不泄漏堆栈。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    private final String frontendOrigin;

    public AdminSecurityConfig(@Value("${site.origin:}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin == null ? "" : frontendOrigin.trim();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminTokenAuthenticationFilter adminTokenAuthenticationFilter,
            AdminOAuthSuccessHandler oauthSuccessHandler) throws Exception {
        http.csrf().disable()
                .cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers(
                        "/api/admin/auth/exchange",
                        "/oauth2/**",
                        "/login/**").permitAll()
                .antMatchers("/api/admin/**").authenticated()
                .anyRequest().permitAll()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(restAuthenticationEntryPoint())
                .accessDeniedHandler(restAccessDeniedHandler())
                .and()
                .oauth2Login()
                .successHandler(oauthSuccessHandler)
                .failureHandler((request, response, exception) ->
                        response.sendRedirect(frontendOrigin + "/admin/login?error=oauth_failed"));

        http.addFilterBefore(adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * ClientRegistrationRepository（#16）：从环境变量读取 GitHub OAuth 凭据；
     * 未配置时提供空仓库，公开站点仍可按 MVP 文件读路径启动。
     */
    @Bean
    public ClientRegistrationRepository siteClientRegistrationRepository(
            @Value("${GITHUB_OAUTH_CLIENT_ID:}") String clientId,
            @Value("${GITHUB_OAUTH_CLIENT_SECRET:}") String clientSecret) {
        if (!StringUtils.hasText(clientId)) {
            // 空仓库：未配置 GitHub OAuth 凭据时公开站点仍可按 MVP 文件读路径启动
            return registrationId -> null;
        }
        ClientRegistration registration = CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId(clientId.trim())
                .clientSecret(clientSecret == null ? "" : clientSecret.trim())
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /**
     * 放宽编码百分号（#5 契约：畸形 slug 如 {@code with%20space} 必须返回 JSON
     * 404，而不是被防火墙整体拒绝）；路径穿越（{@code ..}、{@code //}、编码
     * 斜杠/反斜杠、空字节等）仍按严格防火墙默认拒绝。
     */
    @Bean
    public HttpFirewall siteHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }

    private AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> writeJsonError(response, 401, "unauthorized");
    }

    private AccessDeniedHandler restAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJsonError(response, 403, "forbidden");
    }

    private static void writeJsonError(HttpServletResponse response, int status, String error)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}