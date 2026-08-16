package com.myblog.backend.controller;

import com.myblog.backend.pojo.TokenResult;
import com.myblog.backend.service.AdminSessionService;
import com.myblog.backend.service.InvalidExchangeCodeException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Admin 认证 API（#16）：会话状态、一次性交换码换取令牌、登出撤销。
 *
 * <p>令牌只经响应体与 {@code Authorization} 头传输，不出现 URL 或日志；
 * 服务端只保存哈希（{@link AdminSessionService}）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final AdminSessionService sessionService;

    public AdminAuthController(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 当前会话身份（需要有效令牌）。 */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        // 只有通过 AdminTokenAuthenticationFilter 校验的请求能到达这里
        String login = ((com.myblog.backend.pojo.AdminPrincipal) authentication.getPrincipal()).getLogin();
        return ResponseEntity.ok().cacheControl(NO_STORE)
                .body(Map.of("login", login));
    }

    /** 用一次性交换码换取 8 小时有效的随机不透明会话令牌。 */
    @PostMapping("/auth/exchange")
    public ResponseEntity<?> exchange(@RequestBody(required = false) Map<String, String> body) {
        if (!sessionService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "database_unavailable"));
        }
        String code = body == null ? null : body.get("code");
        try {
            TokenResult result = sessionService.exchange(code);
            return ResponseEntity.ok().cacheControl(NO_STORE)
                    .body(Map.of(
                            "token", result.token,
                            "login", result.login,
                            "expiresIn", result.expiresInSeconds));
        } catch (InvalidExchangeCodeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(NO_STORE)
                    .body(Map.of("error", "invalid_code"));
        }
    }

    /** 撤销当前令牌（登出）；令牌不存在时结果同样是“不再有效”。 */
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            sessionService.revoke(header.substring(7).trim());
        }
        return ResponseEntity.ok().cacheControl(NO_STORE).body(Map.of("ok", true));
    }
}