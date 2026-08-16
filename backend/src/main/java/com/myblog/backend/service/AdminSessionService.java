package com.myblog.backend.service;
import com.myblog.backend.utils.TokenUtil;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Site Owner 会话服务（#16）：一次性交换码与不透明会话令牌。
 *
 * <p>服务端只保存 SHA-256 哈希（{@link TokenUtil}），原始秘密只经
 * HTTP 响应 / Authorization 头传输，不落库、不写日志。交换码一次性使用；
 * 会话令牌 8 小时有效且可撤销。
 */
@Service
public class AdminSessionService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final Duration exchangeCodeTtl;
    private final Duration sessionTtl;

    public AdminSessionService(
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            @Value("${site.admin.exchange-code-ttl:5m}") Duration exchangeCodeTtl,
            @Value("${site.admin.session-ttl:8h}") Duration sessionTtl) {
        this.jdbcTemplate = jdbcTemplate;
        this.exchangeCodeTtl = exchangeCodeTtl;
        this.sessionTtl = sessionTtl;
    }

    /** 数据库读路径是否可用（未配置数据源时管理端一律拒绝，fail closed）。 */
    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 为通过 allowlist 校验的 GitHub 身份创建一次性交换码，返回原始码。 */
    public String createExchangeCode(String ownerLogin) {
        JdbcTemplate jdbc = requireJdbc();
        purgeExpired(jdbc);
        String code = TokenUtil.randomSecret();
        jdbc.update(
                "INSERT INTO admin_oauth_codes (code_hash, owner_login, expires_at)"
                        + " VALUES (?, ?, ?)",
                TokenUtil.sha256Hex(code), ownerLogin,
                OffsetDateTime.now().plus(exchangeCodeTtl));
        return code;
    }

    /**
     * 用一次性交换码换取会话令牌。
     *
     * @throws InvalidExchangeCodeException 码无效、已使用或已过期
     */
    public TokenResult exchange(String rawCode) {
        JdbcTemplate jdbc = requireJdbc();
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidExchangeCodeException();
        }
        // 条件更新保证一次性：只有未使用且未过期的码才能被消费并取回身份
        String ownerLogin = jdbc.query(
                "UPDATE admin_oauth_codes"
                        + "   SET used_at = now()"
                        + " WHERE code_hash = ? AND used_at IS NULL AND expires_at > now()"
                        + " RETURNING owner_login",
                rs -> rs.next() ? rs.getString("owner_login") : null,
                TokenUtil.sha256Hex(rawCode.trim()));
        if (ownerLogin == null) {
            throw new InvalidExchangeCodeException();
        }
        String token = TokenUtil.randomSecret();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(sessionTtl);
        jdbc.update(
                "INSERT INTO admin_sessions (token_hash, owner_login, expires_at)"
                        + " VALUES (?, ?, ?)",
                TokenUtil.sha256Hex(token), ownerLogin, expiresAt);
        return new TokenResult(token, ownerLogin, sessionTtl.getSeconds(), expiresAt);
    }

    /**
     * 校验会话令牌：存在、未撤销且未过期时返回身份，否则返回 {@code null}。
     * 任何非不透明令牌（如 legacy JWT）都会因哈希不匹配而失败。
     */
    public String authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !isAvailable()) {
            return null;
        }
        return jdbcTemplate.getIfAvailable().query(
                "SELECT owner_login FROM admin_sessions"
                        + " WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()",
                rs -> rs.next() ? rs.getString("owner_login") : null,
                TokenUtil.sha256Hex(rawToken.trim()));
    }

    /** 撤销会话令牌（登出）；未找到令牌时静默成功（结果始终是“不再有效”）。 */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !isAvailable()) {
            return;
        }
        jdbcTemplate.getIfAvailable().update(
                "UPDATE admin_sessions SET revoked_at = now()"
                        + " WHERE token_hash = ? AND revoked_at IS NULL",
                TokenUtil.sha256Hex(rawToken.trim()));
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：管理端会话不可用");
        }
        return jdbc;
    }

    private void purgeExpired(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM admin_oauth_codes WHERE expires_at <= now()");
        jdbc.update("DELETE FROM admin_sessions WHERE expires_at <= now()");
    }

    /** 交换结果：原始会话令牌只在响应体中返回一次。 */
    public static class TokenResult {

        public final String token;
        public final String login;
        public final long expiresInSeconds;
        public final OffsetDateTime expiresAt;

        TokenResult(String token, String login, long expiresInSeconds, OffsetDateTime expiresAt) {
            this.token = token;
            this.login = login;
            this.expiresInSeconds = expiresInSeconds;
            this.expiresAt = expiresAt;
        }
    }
}