package com.myblog.backend.service;

import com.myblog.backend.utils.TokenUtil;

import java.time.OffsetDateTime;

/**
 * Site Owner 会话服务契约（#16）：一次性交换码与不透明会话令牌。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.AdminSessionServiceImpl}。
 * 服务端只保存 SHA-256 哈希（{@link TokenUtil}），原始秘密只经 HTTP 响应 /
 * Authorization 头传输，不落库、不写日志。交换码一次性使用；会话令牌
 * 8 小时有效且可撤销。
 */
public interface AdminSessionService {

    /** 数据库读路径是否可用（未配置数据源时管理端一律拒绝，fail closed）。 */
    boolean isAvailable();

    /** 为通过 allowlist 校验的 GitHub 身份创建一次性交换码，返回原始码。 */
    String createExchangeCode(String ownerLogin);

    /**
     * 用一次性交换码换取会话令牌。
     *
     * @throws InvalidExchangeCodeException 码无效、已使用或已过期
     */
    TokenResult exchange(String rawCode);

    /**
     * 校验会话令牌：存在、未撤销且未过期时返回身份，否则返回 {@code null}。
     * 任何非不透明令牌（如 legacy JWT）都会因哈希不匹配而失败。
     */
    String authenticate(String rawToken);

    /** 撤销会话令牌（登出）；未找到令牌时静默成功（结果始终是“不再有效”）。 */
    void revoke(String rawToken);

    /** 交换结果：原始会话令牌只在响应体中返回一次。 */
    class TokenResult {

        public final String token;
        public final String login;
        public final long expiresInSeconds;
        public final OffsetDateTime expiresAt;

        public TokenResult(String token, String login, long expiresInSeconds, OffsetDateTime expiresAt) {
            this.token = token;
            this.login = login;
            this.expiresInSeconds = expiresInSeconds;
            this.expiresAt = expiresAt;
        }
    }
}
