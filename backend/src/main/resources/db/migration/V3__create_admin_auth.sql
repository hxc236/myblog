-- #16 切片：Site Owner 登录会话（GitHub OAuth 一次性交换码 + 不透明会话令牌）。
--
-- 服务端只保存 SHA-256 哈希，原始交换码与会话令牌都不落库、不落日志；
-- 交换码一次性使用（used_at），令牌可撤销（revoked_at）并有过期时间。

-- 一次性交换码：OAuth 回调成功后发给 Vue，用于换取会话令牌。
CREATE TABLE admin_oauth_codes (
    id          BIGSERIAL PRIMARY KEY,
    code_hash   TEXT NOT NULL,
    owner_login TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_oauth_codes_code_hash UNIQUE (code_hash)
);
CREATE INDEX idx_admin_oauth_codes_expires_at ON admin_oauth_codes (expires_at);

-- 会话令牌：8 小时有效，撤销或过期后立即失效。
CREATE TABLE admin_sessions (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  TEXT NOT NULL,
    owner_login TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_sessions_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_admin_sessions_expires_at ON admin_sessions (expires_at);
