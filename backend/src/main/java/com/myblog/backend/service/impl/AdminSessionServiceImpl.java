package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.AdminSessionMapper;
import com.myblog.backend.pojo.TokenResult;
import com.myblog.backend.service.AdminSessionService;
import com.myblog.backend.service.InvalidExchangeCodeException;
import com.myblog.backend.utils.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Site Owner 会话服务实现（#16）：一次性交换码与不透明会话令牌。
 *
 * <p>服务端只保存 SHA-256 哈希（{@link TokenUtil}），原始秘密只经
 * HTTP 响应 / Authorization 头传输，不落库、不写日志。交换码一次性使用；
 * 会话令牌 8 小时有效且可撤销。数据访问见 {@link AdminSessionMapper}。
 */
@Service
public class AdminSessionServiceImpl implements AdminSessionService {

    private final AdminSessionMapper mapper;
    private final Duration exchangeCodeTtl;
    private final Duration sessionTtl;

    public AdminSessionServiceImpl(
            AdminSessionMapper mapper,
            @Value("${site.admin.exchange-code-ttl:5m}") Duration exchangeCodeTtl,
            @Value("${site.admin.session-ttl:8h}") Duration sessionTtl) {
        this.mapper = mapper;
        this.exchangeCodeTtl = exchangeCodeTtl;
        this.sessionTtl = sessionTtl;
    }

    /** 数据库读路径是否可用（未配置数据源时管理端一律拒绝，fail closed）。 */
    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /** 为通过 allowlist 校验的 GitHub 身份创建一次性交换码，返回原始码。 */
    public String createExchangeCode(String ownerLogin) {
        mapper.purgeExpired();
        String code = TokenUtil.randomSecret();
        mapper.insertExchangeCode(
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
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidExchangeCodeException();
        }
        // 条件更新保证一次性：只有未使用且未过期的码才能被消费并取回身份
        String ownerLogin = mapper.consumeExchangeCode(TokenUtil.sha256Hex(rawCode.trim()));
        if (ownerLogin == null) {
            throw new InvalidExchangeCodeException();
        }
        String token = TokenUtil.randomSecret();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(sessionTtl);
        mapper.insertSession(TokenUtil.sha256Hex(token), ownerLogin, expiresAt);
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
        return mapper.findSessionOwner(TokenUtil.sha256Hex(rawToken.trim()));
    }

    /** 撤销会话令牌（登出）；未找到令牌时静默成功（结果始终是“不再有效”）。 */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !isAvailable()) {
            return;
        }
        mapper.revokeSession(TokenUtil.sha256Hex(rawToken.trim()));
    }
}
