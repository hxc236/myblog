package com.myblog.backend.config;
import com.myblog.backend.service.AdminSessionService;
import com.myblog.backend.pojo.AdminPrincipal;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Admin API 不透明会话令牌过滤器（#16）。
 *
 * <p>只处理 {@code /api/admin/**}（一次性交换码端点除外）：解析
 * {@code Authorization: Bearer <token>}，通过 {@link AdminSessionService}
 * 校验哈希、过期与撤销状态。校验失败时不设置认证上下文，由安全链的
 * 401 入口点拒绝；任何非不透明令牌（包括 legacy JWT）都会因哈希不匹配
 * 而失败，永远不会被解析或信任。
 */
@Component
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AdminSessionService sessionService;

    public AdminTokenAuthenticationFilter(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            String login = sessionService.authenticate(token);
            if (login != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                new AdminPrincipal(login), token,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/") || "/api/admin/auth/exchange".equals(path);
    }
}