package com.myblog.backend.mapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Admin 会话数据访问（#16）：一次性交换码与不透明会话令牌。
 *
 * <p>服务端只保存 SHA-256 哈希；交换码一次性使用（条件更新保证），会话
 * 令牌 8 小时有效且可撤销。过期记录在创建/交换时惰性清理。
 */
@Component
public class AdminSessionMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public AdminSessionMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 惰性清理已过期的一次性交换码与会话令牌。 */
    public void purgeExpired() {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM admin_oauth_codes WHERE expires_at <= now()");
        jdbc.update("DELETE FROM admin_sessions WHERE expires_at <= now()");
    }

    /** 写入一次性交换码（只存哈希）。 */
    public void insertExchangeCode(String codeHash, String ownerLogin, OffsetDateTime expiresAt) {
        requireJdbc().update(
                "INSERT INTO admin_oauth_codes (code_hash, owner_login, expires_at)"
                        + " VALUES (?, ?, ?)",
                codeHash, ownerLogin, expiresAt);
    }

    /**
     * 消费一次性交换码：只有未使用且未过期的码才能被消费并取回身份。
     *
     * @return 拥有者 GitHub 登录名，码无效时返回 null
     */
    public String consumeExchangeCode(String codeHash) {
        return requireJdbc().query(
                "UPDATE admin_oauth_codes"
                        + "   SET used_at = now()"
                        + " WHERE code_hash = ? AND used_at IS NULL AND expires_at > now()"
                        + " RETURNING owner_login",
                rs -> rs.next() ? rs.getString("owner_login") : null,
                codeHash);
    }

    /** 写入会话令牌（只存哈希）。 */
    public void insertSession(String tokenHash, String ownerLogin, OffsetDateTime expiresAt) {
        requireJdbc().update(
                "INSERT INTO admin_sessions (token_hash, owner_login, expires_at)"
                        + " VALUES (?, ?, ?)",
                tokenHash, ownerLogin, expiresAt);
    }

    /** 校验会话令牌：存在、未撤销且未过期时返回身份，否则返回 null。 */
    public String findSessionOwner(String tokenHash) {
        return requireJdbc().query(
                "SELECT owner_login FROM admin_sessions"
                        + " WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()",
                rs -> rs.next() ? rs.getString("owner_login") : null,
                tokenHash);
    }

    /** 撤销会话令牌（登出）；未找到令牌时静默成功。 */
    public void revokeSession(String tokenHash) {
        requireJdbc().update(
                "UPDATE admin_sessions SET revoked_at = now()"
                        + " WHERE token_hash = ? AND revoked_at IS NULL",
                tokenHash);
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：管理端会话不可用");
        }
        return jdbc;
    }
}
