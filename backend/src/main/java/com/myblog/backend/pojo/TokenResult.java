package com.myblog.backend.pojo;

import java.time.OffsetDateTime;

/** 一次性交换码换取的会话令牌结果（#16）：原始令牌只在响应体中返回一次。 */
public class TokenResult {

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
