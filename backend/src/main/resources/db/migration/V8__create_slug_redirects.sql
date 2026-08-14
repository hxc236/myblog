-- #22 切片：slug 历史重定向。
--
-- 修改公开 slug 时写入全局唯一的历史重定向；旧 slug 对已发布文章返回
-- 永久 301（#14 实现决策）。归档后（published_revision_id 置空）旧 slug
-- 与当前 slug 都返回站内 404，因此 301 只指向“当前仍已发布”的目标。

CREATE TABLE post_slug_redirects (
    id         BIGSERIAL PRIMARY KEY,
    old_slug   TEXT NOT NULL,
    post_id    BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_slug_redirects_old_slug UNIQUE (old_slug)
);
