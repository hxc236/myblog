package com.myblog.publicsite.posts;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Blog Post 服务（#20）：Draft 生命周期与立即发布。
 *
 * <p>修订模型（#14 4.1/4.2）：{@code posts} 保存身份与发布指针；草稿修订
 * 可编辑，Published Revision 不可变。保存 Draft 不触碰公开指针；发布在单一
 * 事务内原子切换 published_revision_id、发布时间并替换搜索投影。Markdown
 * 正文以 TEXT 保存在 post_revisions，不产生第二权威内容源。
 */
@Service
public class PostService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 64;

    /** 草稿保存载荷（#20 编辑表单）。 */
    public static class DraftPayload {

        public String title;
        public String summary;
        public String bodyMarkdown;
        public String slug;
        public Long categoryId;
        public List<Long> tagIds;
    }

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public PostService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 新建 Blog Post：自动进入 Draft（空修订），slug 留待保存时设置。 */
    @Transactional
    public AdminPostDetail createPost() {
        JdbcTemplate jdbc = requireJdbc();
        Long postId = jdbc.queryForObject(
                "INSERT INTO posts (slug) VALUES (NULL) RETURNING id", Long.class);
        Long revisionId = jdbc.queryForObject(
                "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                        + " VALUES (?, 1, '', '', '') RETURNING id",
                Long.class, postId);
        jdbc.update("UPDATE posts SET draft_revision_id = ? WHERE id = ?", revisionId, postId);
        return getPostDetail(postId);
    }

    /** 管理端列表：标题取 Draft（有则）否则取 Published Revision。 */
    public List<AdminPostSummary> listPosts() {
        JdbcTemplate jdbc = requireJdbc();
        List<AdminPostSummary> result = new ArrayList<>();
        jdbc.query(
                "SELECT p.id, p.slug, p.category_id, p.last_published_at, p.updated_at,"
                        + " c.name AS category_name,"
                        + " (p.draft_revision_id IS NOT NULL) AS has_draft,"
                        + " (p.published_revision_id IS NOT NULL) AS has_published,"
                        + " COALESCE(dr.title, pr.title, '') AS title"
                        + "  FROM posts p"
                        + "  LEFT JOIN post_revisions dr ON dr.id = p.draft_revision_id"
                        + "  LEFT JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + " ORDER BY p.updated_at DESC, p.id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    AdminPostSummary s = new AdminPostSummary();
                    s.id = rs.getLong("id");
                    s.slug = rs.getString("slug");
                    s.title = rs.getString("title");
                    boolean hasDraft = rs.getBoolean("has_draft");
                    boolean hasPublished = rs.getBoolean("has_published");
                    s.state = hasDraft && hasPublished ? "draft_published"
                            : hasDraft ? "draft" : "published";
                    long categoryId = rs.getLong("category_id");
                    s.categoryId = rs.wasNull() ? null : categoryId;
                    s.categoryName = rs.getString("category_name");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    s.publishedAt = publishedAt == null ? null : publishedAt.toString();
                    OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
                    s.updatedAt = updatedAt == null ? null : updatedAt.toString();
                    s.tagIds = new ArrayList<>();
                    result.add(s);
                });
        if (!result.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(result.size(), "?"));
            jdbc.query(
                    "SELECT post_id, tag_id FROM post_tags WHERE post_id IN (" + placeholders + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        for (AdminPostSummary s : result) {
                            if (s.id.equals(rs.getLong("post_id"))) {
                                s.tagIds.add(rs.getLong("tag_id"));
                                break;
                            }
                        }
                    },
                    result.stream().map(s -> s.id).toArray());
        }
        return result;
    }

    /** 管理端详情（仅 Site Owner）：优先返回当前 Draft，否则返回 Published Revision。 */
    public AdminPostDetail getPostDetail(long id) {
        JdbcTemplate jdbc = requireJdbc();
        AdminPostDetail detail = jdbc.query(
                "SELECT p.id, p.slug, p.category_id, p.last_published_at, p.updated_at,"
                        + " c.name AS category_name,"
                        + " (p.draft_revision_id IS NOT NULL) AS has_draft,"
                        + " (p.published_revision_id IS NOT NULL) AS has_published,"
                        + " COALESCE(dr.title, pr.title, '') AS title,"
                        + " COALESCE(dr.summary, pr.summary, '') AS summary,"
                        + " COALESCE(dr.body_markdown, pr.body_markdown, '') AS body_markdown"
                        + "  FROM posts p"
                        + "  LEFT JOIN post_revisions dr ON dr.id = p.draft_revision_id"
                        + "  LEFT JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + " WHERE p.id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    AdminPostDetail d = new AdminPostDetail();
                    d.id = rs.getLong("id");
                    d.slug = rs.getString("slug");
                    d.title = rs.getString("title");
                    d.summary = rs.getString("summary");
                    d.bodyMarkdown = rs.getString("body_markdown");
                    boolean hasDraft = rs.getBoolean("has_draft");
                    boolean hasPublished = rs.getBoolean("has_published");
                    d.state = hasDraft && hasPublished ? "draft_published"
                            : hasDraft ? "draft" : "published";
                    long categoryId = rs.getLong("category_id");
                    d.categoryId = rs.wasNull() ? null : categoryId;
                    d.categoryName = rs.getString("category_name");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    d.publishedAt = publishedAt == null ? null : publishedAt.toString();
                    OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
                    d.updatedAt = updatedAt == null ? null : updatedAt.toString();
                    d.tagIds = new ArrayList<>();
                    return d;
                },
                id);
        if (detail == null) {
            return null;
        }
        jdbc.query(
                "SELECT tag_id FROM post_tags WHERE post_id = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        detail.tagIds.add(rs.getLong("tag_id")),
                id);
        return detail;
    }

    /**
     * 保存 Draft：首次编辑已发布文章时先创建新的 Draft 修订（Published
     * Revision 保持不可变）；保存不触碰公开指针、发布时间与搜索投影。
     */
    @Transactional
    public AdminPostDetail saveDraft(long id, DraftPayload payload) {
        JdbcTemplate jdbc = requireJdbc();
        Map<String, Object> post = jdbc.query(
                "SELECT id, draft_revision_id FROM posts WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("draft_revision_id", rs.getObject("draft_revision_id"));
                    return m;
                },
                id);
        if (post == null) {
            return null;
        }
        validateDraft(payload, id, jdbc);

        Long draftRevisionId = (Long) post.get("draft_revision_id");
        if (draftRevisionId == null) {
            Integer maxNo = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(revision_no), 0) FROM post_revisions WHERE post_id = ?",
                    Integer.class, id);
            draftRevisionId = jdbc.queryForObject(
                    "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                            + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, id, maxNo + 1,
                    payload.title.trim(), payload.summary == null ? "" : payload.summary.trim(),
                    payload.bodyMarkdown == null ? "" : payload.bodyMarkdown);
        } else {
            jdbc.update(
                    "UPDATE post_revisions SET title = ?, summary = ?, body_markdown = ?"
                            + " WHERE id = ?",
                    payload.title.trim(),
                    payload.summary == null ? "" : payload.summary.trim(),
                    payload.bodyMarkdown == null ? "" : payload.bodyMarkdown,
                    draftRevisionId);
        }

        jdbc.update(
                "UPDATE posts SET slug = ?, category_id = ?, draft_revision_id = ?,"
                        + " updated_at = now() WHERE id = ?",
                payload.slug == null || payload.slug.trim().isEmpty() ? null : payload.slug.trim(),
                payload.categoryId, draftRevisionId, id);

        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", id);
        if (payload.tagIds != null) {
            for (Long tagId : payload.tagIds) {
                jdbc.update(
                        "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", id, tagId);
            }
        }
        return getPostDetail(id);
    }

    /**
     * 立即发布：事务内原子切换 Published Revision 指针、首次/最近发布时间，
     * 并替换搜索投影；发布后原修订不可变（#14 实现决策）。
     */
    @Transactional
    public AdminPostDetail publish(long id) {
        JdbcTemplate jdbc = requireJdbc();
        Map<String, Object> post = jdbc.query(
                "SELECT id, slug, category_id, draft_revision_id, first_published_at"
                        + "  FROM posts WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("slug", rs.getString("slug"));
                    m.put("category_id", rs.getObject("category_id"));
                    m.put("draft_revision_id", rs.getObject("draft_revision_id"));
                    m.put("first_published_at", rs.getObject("first_published_at"));
                    return m;
                },
                id);
        if (post == null) {
            return null;
        }
        Long draftRevisionId = (Long) post.get("draft_revision_id");
        if (draftRevisionId == null) {
            throw new IllegalArgumentException("没有可发布的 Draft");
        }
        if (post.get("category_id") == null) {
            throw new IllegalArgumentException("发布必须选择分类");
        }
        String slug = (String) post.get("slug");
        validateSlugForPublish(slug, id, jdbc);

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "UPDATE posts SET published_revision_id = ?, draft_revision_id = NULL,"
                        + " first_published_at = COALESCE(first_published_at, ?),"
                        + " last_published_at = ?, updated_at = ? WHERE id = ?",
                draftRevisionId, now, now, now, id);

        AdminPostDetail published = jdbc.query(
                "SELECT title, summary FROM post_revisions WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    AdminPostDetail d = new AdminPostDetail();
                    d.title = rs.getString("title");
                    d.summary = rs.getString("summary");
                    return d;
                },
                draftRevisionId);
        jdbc.update("DELETE FROM post_search_documents WHERE post_id = ?", id);
        jdbc.update(
                "INSERT INTO post_search_documents (post_id, title, summary, updated_at)"
                        + " VALUES (?, ?, ?, ?)",
                id, published.title, published.summary, now);
        return getPostDetail(id);
    }

    // ---- 内部 ----

    private void validateDraft(DraftPayload payload, long postId, JdbcTemplate jdbc) {
        if (payload == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        requireText(payload.title, 200, "标题");
        if (payload.summary != null && payload.summary.length() > 500) {
            throw new IllegalArgumentException("摘要最多 500 字符");
        }
        if (payload.bodyMarkdown != null && payload.bodyMarkdown.length() > 200_000) {
            throw new IllegalArgumentException("Markdown 正文过长");
        }
        if (payload.slug != null && !payload.slug.trim().isEmpty()) {
            validateSlugForPublish(payload.slug.trim(), postId, jdbc);
        }
        if (payload.tagIds != null && payload.tagIds.size() > 10) {
            throw new IllegalArgumentException("每篇文章最多 10 个标签");
        }
    }

    private void validateSlugForPublish(String slug, long postId, JdbcTemplate jdbc) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("发布必须设置 slug");
        }
        if (slug.length() > MAX_SLUG_LENGTH
                || !SLUG_PATTERN.matcher(slug.toLowerCase(Locale.ROOT)).matches()
                || !slug.equals(slug.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "slug 必须是 64 字符以内的小写字母、数字与连字符组合");
        }
        Long conflict = jdbc.query(
                "SELECT id FROM posts WHERE slug = ? AND id <> ?",
                rs -> rs.next() ? rs.getLong("id") : null, slug, postId);
        if (conflict != null) {
            throw new IllegalArgumentException("slug 已被其他文章使用：" + slug);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " 最多 " + maxLength + " 字符");
        }
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Blog Post 服务不可用");
        }
        return jdbc;
    }
}
