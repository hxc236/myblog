-- #19 切片：Category、Tag 与 Uncategorized Category。
--
-- Blog Post 身份表（#14 4.1）在本切片建立，用于保证“删除分类/标签时内容
-- 关系保持正确”；post_revisions 与 draft/published 修订指针的 FK 由 #20
-- 补充（发布语义属于 #20）。

-- 分类：少量、稳定、层级扁平；名称唯一；内置 Uncategorized（is_uncategorized）。
CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    name            TEXT NOT NULL,
    is_uncategorized BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- 标签：可复用主题标签，slug 唯一（由名称自动生成，冲突时加后缀）。
CREATE TABLE tags (
    id         BIGSERIAL PRIMARY KEY,
    slug       TEXT NOT NULL,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tags_slug UNIQUE (slug)
);

-- Blog Post 身份：公开性由 published_revision_id 决定（#14 4.1）。
-- revision 外键在 #20 建立 post_revisions 后补充。
CREATE TABLE posts (
    id                   BIGSERIAL PRIMARY KEY,
    slug                 TEXT NOT NULL,
    category_id          BIGINT REFERENCES categories (id),
    draft_revision_id    BIGINT,
    published_revision_id BIGINT,
    first_published_at   TIMESTAMPTZ,
    last_published_at    TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_posts_slug UNIQUE (slug)
);

-- Blog Post 与 Tag 的多对多关系：删除任一侧都自动解除关联。
CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag ON post_tags (tag_id);

-- 系统内置的 Uncategorized Category：不可删除（服务层按 is_uncategorized
-- 拒绝），删除正在使用的 Category 时关联文章迁移到这里。
INSERT INTO categories (name, is_uncategorized) VALUES ('未分类', true);
