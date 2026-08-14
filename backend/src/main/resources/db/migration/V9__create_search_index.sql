-- #23 切片：搜索投影索引与重建能力。
--
-- pg_trgm GIN 索引加速 3 字符及以上查询的 ILIKE；1–2 字符短查询使用
-- 有结果上限的 ILIKE（#14 实现决策）。搜索投影只保存 Published Revision
-- 的标题/摘要/更新时间，可从 post_revisions 全量重建，不是第二权威源。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_search_documents_trgm
    ON post_search_documents USING gin (title gin_trgm_ops, summary gin_trgm_ops);
