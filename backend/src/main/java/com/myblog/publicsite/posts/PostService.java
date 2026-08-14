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

    /** 管理端列表：标题取 Draft（有则）否则取 Published Revision，归档后取最新修订。 */
    public List<AdminPostSummary> listPosts() {
        JdbcTemplate jdbc = requireJdbc();
        List<AdminPostSummary> result = new ArrayList<>();
        jdbc.query(
                "SELECT p.id, p.slug, p.category_id, p.last_published_at, p.updated_at,"
                        + " p.first_published_at, c.name AS category_name,"
                        + " (p.draft_revision_id IS NOT NULL) AS has_draft,"
                        + " (p.published_revision_id IS NOT NULL) AS has_published,"
                        + " COALESCE(dr.title, pr.title, latest.title, '') AS title"
                        + "  FROM posts p"
                        + "  LEFT JOIN post_revisions dr ON dr.id = p.draft_revision_id"
                        + "  LEFT JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN LATERAL (SELECT title FROM post_revisions lr"
                        + "    WHERE lr.post_id = p.id ORDER BY lr.revision_no DESC LIMIT 1) latest ON true"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + " ORDER BY p.updated_at DESC, p.id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    AdminPostSummary s = new AdminPostSummary();
                    s.id = rs.getLong("id");
                    s.slug = rs.getString("slug");
                    s.title = rs.getString("title");
                    boolean hasDraft = rs.getBoolean("has_draft");
                    boolean hasPublished = rs.getBoolean("has_published");
                    boolean hasPublishedHistory = rs.getObject("first_published_at") != null;
                    s.state = hasPublished && hasDraft ? "draft_published"
                            : hasPublished ? "published"
                            : hasPublishedHistory ? "archived"
                            : "draft";
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
                        + " p.first_published_at, c.name AS category_name,"
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
                    boolean hasPublishedHistory = rs.getObject("first_published_at") != null;
                    d.state = hasPublished && hasDraft ? "draft_published"
                            : hasPublished ? "published"
                            : hasPublishedHistory ? "archived"
                            : "draft";
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
     * 修改已发布文章的 slug 时写入全局唯一的历史重定向（#14 实现决策）。
     */
    @Transactional
    public AdminPostDetail saveDraft(long id, DraftPayload payload) {
        JdbcTemplate jdbc = requireJdbc();
        Map<String, Object> post = jdbc.query(
                "SELECT id, slug, draft_revision_id, published_revision_id"
                        + "  FROM posts WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("slug", rs.getString("slug"));
                    m.put("draft_revision_id", rs.getObject("draft_revision_id"));
                    m.put("published_revision_id", rs.getObject("published_revision_id"));
                    return m;
                },
                id);
        if (post == null) {
            return null;
        }
        validateDraft(payload, id, jdbc);

        String newSlug = payload.slug == null || payload.slug.trim().isEmpty()
                ? null : payload.slug.trim();
        String currentSlug = (String) post.get("slug");
        if (post.get("published_revision_id") != null
                && currentSlug != null && !currentSlug.isEmpty()
                && !currentSlug.equals(newSlug)) {
            // 公开 slug 变更：旧 slug 永久 301 到当前 slug
            jdbc.update(
                    "INSERT INTO post_slug_redirects (old_slug, post_id) VALUES (?, ?)",
                    currentSlug, id);
        }

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
                newSlug,
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

    /** 修订历史（#22）：全部不可变修订，标记当前已发布的那个。 */
    public List<RevisionItem> listRevisions(long id) {
        JdbcTemplate jdbc = requireJdbc();
        List<RevisionItem> result = new ArrayList<>();
        jdbc.query(
                "SELECT pr.id, pr.revision_no, pr.title, pr.summary, pr.created_at,"
                        + " (pr.id = p.published_revision_id) AS is_published"
                        + "  FROM post_revisions pr"
                        + "  JOIN posts p ON p.id = pr.post_id"
                        + " WHERE pr.post_id = ?"
                        + " ORDER BY pr.revision_no DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    RevisionItem r = new RevisionItem();
                    r.revisionId = rs.getLong("id");
                    r.revisionNo = rs.getInt("revision_no");
                    r.title = rs.getString("title");
                    r.summary = rs.getString("summary");
                    r.published = rs.getBoolean("is_published");
                    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                    r.createdAt = createdAt == null ? null : createdAt.toString();
                    result.add(r);
                },
                id);
        return result;
    }

    /**
     * 恢复历史修订（#22）：把目标修订复制为新的 Draft，预览确认后再次发布；
     * 不直接覆盖线上内容。
     */
    @Transactional
    public AdminPostDetail restoreRevision(long id, long revisionId) {
        JdbcTemplate jdbc = requireJdbc();
        Integer belongs = jdbc.query(
                "SELECT 1 FROM post_revisions WHERE id = ? AND post_id = ?",
                rs -> rs.next() ? 1 : null, revisionId, id);
        if (belongs == null) {
            return null;
        }
        Integer maxNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(revision_no), 0) FROM post_revisions WHERE post_id = ?",
                Integer.class, id);
        Long newDraftId = jdbc.queryForObject(
                "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                        + " SELECT ?, ?, title, summary, body_markdown FROM post_revisions WHERE id = ?"
                        + " RETURNING id",
                Long.class, id, maxNo + 1, revisionId);
        jdbc.update(
                "UPDATE posts SET draft_revision_id = ?, updated_at = now() WHERE id = ?",
                newDraftId, id);
        return getPostDetail(id);
    }

    /** 撤回已发布 Blog Post 并保留为 Archived Post（#22）：公开指针置空并移除搜索投影。 */
    @Transactional
    public AdminPostDetail archive(long id) {
        JdbcTemplate jdbc = requireJdbc();
        Integer published = jdbc.query(
                "SELECT 1 FROM posts WHERE id = ? AND published_revision_id IS NOT NULL",
                rs -> rs.next() ? 1 : null, id);
        if (published == null) {
            return null;
        }
        jdbc.update(
                "UPDATE posts SET published_revision_id = NULL, updated_at = now() WHERE id = ?",
                id);
        jdbc.update("DELETE FROM post_search_documents WHERE post_id = ?", id);
        return getPostDetail(id);
    }

    /**
     * 永久删除：只允许从未发布的 Draft（#14 用户故事 35）；曾发布过的文章
     * 只能归档，保留审计与恢复能力（#14 用户故事 36）。
     */
    @Transactional
    public boolean deletePost(long id) {
        JdbcTemplate jdbc = requireJdbc();
        Integer neverPublished = jdbc.query(
                "SELECT 1 FROM posts WHERE id = ? AND first_published_at IS NULL",
                rs -> rs.next() ? 1 : null, id);
        if (neverPublished == null) {
            throw new IllegalArgumentException("曾发布的文章只能归档，不能删除");
        }
        return jdbc.update("DELETE FROM posts WHERE id = ?", id) > 0;
    }

    /**
     * 从 Published Revision 全量重建搜索投影（#23）：投影不是第二权威源，
     * 损坏时无需恢复第二份内容。
     */
    @Transactional
    public int rebuildSearchIndex() {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM post_search_documents");
        return jdbc.update(
                "INSERT INTO post_search_documents (post_id, title, summary, updated_at)"
                        + " SELECT p.id, pr.title, pr.summary, p.last_published_at"
                        + "   FROM posts p"
                        + "   JOIN post_revisions pr ON pr.id = p.published_revision_id");
    }

    /** 修订历史条目。 */
    public static class RevisionItem {

        public Long revisionId;
        public Integer revisionNo;
        public String title;
        public String summary;
        public String createdAt;
        public Boolean published;
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
        validateMarkdownSafety(payload.bodyMarkdown);
        if (payload.slug != null && !payload.slug.trim().isEmpty()) {
            validateSlugForPublish(payload.slug.trim(), postId, jdbc);
        }
        if (payload.tagIds != null && payload.tagIds.size() > 10) {
            throw new IllegalArgumentException("每篇文章最多 10 个标签");
        }
    }

    /** Markdown 保存时拒绝危险原始 HTML（#24）；渲染后仍由 DOMPurify 兜底。 */
    private void validateMarkdownSafety(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return;
        }
        String lower = markdown.toLowerCase(Locale.ROOT);
        for (String forbidden : new String[]{
                "<script", "<iframe", "<object", "<embed", "<link", "<meta",
                "javascript:", "onerror=", "onload=", "onclick=", "onmouseover=",
                "<svg", "<math"}) {
            if (lower.contains(forbidden)) {
                throw new IllegalArgumentException(
                        "Markdown 包含被禁止的危险 HTML（" + forbidden + "）");
            }
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
        // 旧 slug 永久绑定其文章（#22）：不得被新 slug 复用
        Long redirectConflict = jdbc.query(
                "SELECT post_id FROM post_slug_redirects WHERE old_slug = ?",
                rs -> rs.next() ? rs.getLong("post_id") : null, slug);
        if (redirectConflict != null) {
            throw new IllegalArgumentException("slug 已被历史重定向占用：" + slug);
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
