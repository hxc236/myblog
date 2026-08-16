package com.myblog.backend.config;
import com.myblog.backend.service.AdminSessionService;
import com.myblog.backend.pojo.AdminPrincipal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GitHub OAuth 成功处理器（#16）：认证成功不等于授权，必须再次匹配
 * 唯一 allowlist（#14 用户故事 23/24）。
 *
 * <p>命中 allowlist：发放一次性交换码并重定向到 Admin Console 登录页；
 * 未命中：重定向登录页并携带 {@code error=forbidden}，不开放注册、角色
 * 管理或 Visitor 账户。
 */
@Component
public class AdminOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final AdminSessionService sessionService;
    private final List<String> allowlist;
    private final String frontendOrigin;

    public AdminOAuthSuccessHandler(
            AdminSessionService sessionService,
            @Value("${site.admin.github-allowlist:}") String allowlist,
            @Value("${site.origin:}") String frontendOrigin) {
        this.sessionService = sessionService;
        this.allowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        this.frontendOrigin = frontendOrigin == null ? "" : frontendOrigin.trim();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String login = resolveLogin(authentication);
        if (login != null && allowlist.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(login))) {
            String code = sessionService.createExchangeCode(login);
            response.sendRedirect(frontendOrigin + "/admin/login?code=" + code);
        } else {
            response.sendRedirect(frontendOrigin + "/admin/login?error=forbidden");
        }
    }

    private String resolveLogin(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof OAuth2User) {
                Object login = ((OAuth2User) principal).getAttribute("login");
                if (login != null) {
                    return login.toString();
                }
            }
        }
        return null;
    }
}