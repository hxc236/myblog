-- #24 切片：Media Asset 元数据。
--
-- 生产图片存入私有 R2，PostgreSQL 只保存元数据（#14 实现决策）；object_key
-- 基于内容哈希，公开 URL 不可变并长期缓存。删除保护：被 Draft 或 Published
-- Revision 引用的资源不可删除（服务层扫描修订正文），未引用资源仅标记。

CREATE TABLE media_assets (
    id             BIGSERIAL PRIMARY KEY,
    object_key     TEXT NOT NULL,
    file_name      TEXT NOT NULL,
    mime_type      TEXT NOT NULL,
    size_bytes     BIGINT NOT NULL,
    width          INTEGER NOT NULL,
    height         INTEGER NOT NULL,
    checksum_sha256 TEXT NOT NULL,
    alt_text       TEXT NOT NULL DEFAULT '',
    public_url     TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_media_assets_object_key UNIQUE (object_key),
    CONSTRAINT ck_media_assets_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
