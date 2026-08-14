-- #20 切片：Blog Post 修订、Draft 与发布。
--
-- posts 身份表由 #19 建立；本迁移补充不可变修订、发布指针外键与搜索投影。

-- 不可变内容修订：发布后不再修改；修改已发布文章时先复制为新的 Draft。
CREATE TABLE post_revisions (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    revision_no   INTEGER NOT NULL,
    title         TEXT NOT NULL,
    summary       TEXT NOT NULL,
    body_markdown TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_revisions_post_no UNIQUE (post_id, revision_no)
);

-- 发布指针：公开性由 published_revision_id 决定；Draft 是当前可编辑修订。
ALTER TABLE posts
    ADD CONSTRAINT fk_posts_draft_revision
        FOREIGN KEY (draft_revision_id) REFERENCES post_revisions (id),
    ADD CONSTRAINT fk_posts_published_revision
        FOREIGN KEY (published_revision_id) REFERENCES post_revisions (id);

-- 草稿允许暂不设置 slug（保存/发布时校验）。
ALTER TABLE posts ALTER COLUMN slug DROP NOT NULL;

-- 搜索投影：只保存 Published Revision 的标题、摘要与更新时间；发布事务
-- 同步替换，可从 post_revisions 全量重建（#14 4.4）。
CREATE TABLE post_search_documents (
    post_id    BIGINT PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE,
    title      TEXT NOT NULL,
    summary    TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 公开详情读取路径：按 slug 查已发布文章。
CREATE INDEX idx_posts_published ON posts (published_revision_id);
