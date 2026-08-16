package com.myblog.backend.mapper;

import com.myblog.backend.pojo.AdminPostDetail;
import com.myblog.backend.pojo.AdminPostSummary;
import com.myblog.backend.pojo.PublicPostDetail;
import com.myblog.backend.pojo.PublicPostSummary;
import com.myblog.backend.pojo.RevisionItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blog Post 数据访问（#20/#21/#22/#23）：posts、post_revisions、
 * post_tags、post_slug_redirects 与 post_search_documents。
 *
 * <p>修订模型（#14 4.1/4.2）：{@code posts} 保存身份与发布指针；草稿修订可
 * 编辑，Published Revision 不可变。Markdown 正文以 TEXT 保存在
 * post_revisions，不产生第二权威内容源。搜索投影由 Published Revision
 * 重建，不是第二权威源。
 */
@Component
public class PostMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public PostMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 是否已有任何 Blog Post（一次性导入判空用）。 */
    public boolean hasAnyPost() {
        JdbcTemplate jdbc = requireJdbc();
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM posts LIMIT 1",
                rs -> rs.next() ? 1 : null);
        return hasContent != null;
    }

    // ---- 管理端（PostServiceImpl）----

    /** 新建空 posts 行（slug 留待保存时设置），返回 id。 */
    public Long insertPostWithNullSlug() {
        return requireJdbc().queryForObject(
                "INSERT INTO posts (slug) VALUES (NULL) RETURNING id", Long.class);
    }

    /** 写入一条修订，返回 id。 */
    public Long insertRevision(long postId, int revisionNo, String title, String summary,
                               String bodyMarkdown) {
        return requireJdbc().queryForObject(
                "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, postId, revisionNo, title, summary, bodyMarkdown);
    }

    /** 更新 Draft 修订内容。 */
    public void updateRevision(long revisionId, String title, String summary, String bodyMarkdown) {
        requireJdbc().update(
                "UPDATE post_revisions SET title = ?, summary = ?, body_markdown = ?"
                        + " WHERE id = ?",
                title, summary, bodyMarkdown, revisionId);
    }

    /** 设置 Draft 指针。 */
    public void setDraftRevision(long postId, long revisionId) {
        requireJdbc().update(
                "UPDATE posts SET draft_revision_id = ? WHERE id = ?", revisionId, postId);
    }

    /** 保存 Draft 后更新 posts 行的 slug 与分类。 */
    public void updatePostForDraft(long postId, String slug, Long categoryId, long draftRevisionId) {
        requireJdbc().update(
                "UPDATE posts SET slug = ?, category_id = ?, draft_revision_id = ?,"
                        + " updated_at = now() WHERE id = ?",
                slug, categoryId, draftRevisionId, postId);
    }

    /** 旧 slug 永久 301 到当前 slug（#14 实现决策）。 */
    public void insertSlugRedirect(String oldSlug, long postId) {
        requireJdbc().update(
                "INSERT INTO post_slug_redirects (old_slug, post_id) VALUES (?, ?)",
                oldSlug, postId);
    }

    /** 该文章当前最大修订号（无修订时为 0）。 */
    public int maxRevisionNo(long postId) {
        Integer max = requireJdbc().queryForObject(
                "SELECT COALESCE(MAX(revision_no), 0) FROM post_revisions WHERE post_id = ?",
                Integer.class, postId);
        return max == null ? 0 : max;
    }

    /** 清空并重写文章标签（保存 Draft 时整组替换）。 */
    public void replacePostTags(long postId, List<Long> tagIds) {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", postId);
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                jdbc.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId);
            }
        }
    }

    /** 发布：事务内原子切换 Published Revision 指针、首次/最近发布时间。 */
    public void publishPost(long postId, long publishedRevisionId, OffsetDateTime now) {
        requireJdbc().update(
                "UPDATE posts SET published_revision_id = ?, draft_revision_id = NULL,"
                        + " first_published_at = COALESCE(first_published_at, ?),"
                        + " last_published_at = ?, updated_at = ? WHERE id = ?",
                publishedRevisionId, now, now, now, postId);
    }

    /** 读取一条修订的标题与摘要（发布时构建搜索投影用）。 */
    public AdminPostDetail getRevisionTitleSummary(long revisionId) {
        return requireJdbc().query(
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
                revisionId);
    }

    /** 删除文章搜索投影。 */
    public void deleteSearchDocument(long postId) {
        requireJdbc().update("DELETE FROM post_search_documents WHERE post_id = ?", postId);
    }

    /** 写入文章搜索投影。 */
    public void insertSearchDocument(long postId, String title, String summary,
                                     OffsetDateTime updatedAt) {
        requireJdbc().update(
                "INSERT INTO post_search_documents (post_id, title, summary, updated_at)"
                        + " VALUES (?, ?, ?, ?)",
                postId, title, summary, updatedAt);
    }

    /** 从 Published Revision 全量重建搜索投影（#23），返回重建行数。 */
    public int rebuildSearchIndex() {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM post_search_documents");
        return jdbc.update(
                "INSERT INTO post_search_documents (post_id, title, summary, updated_at)"
                        + " SELECT p.id, pr.title, pr.summary, p.last_published_at"
                        + "   FROM posts p"
                        + "   JOIN post_revisions pr ON pr.id = p.published_revision_id");
    }

    /** 修订是否属于该文章。 */
    public boolean revisionBelongsToPost(long revisionId, long postId) {
        Integer belongs = requireJdbc().query(
                "SELECT 1 FROM post_revisions WHERE id = ? AND post_id = ?",
                rs -> rs.next() ? 1 : null, revisionId, postId);
        return belongs != null;
    }

    /** 把目标修订复制为新修订（恢复历史修订 #22），返回新修订 id。 */
    public Long insertRevisionCopy(long postId, int revisionNo, long sourceRevisionId) {
        return requireJdbc().queryForObject(
                "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                        + " SELECT ?, ?, title, summary, body_markdown FROM post_revisions WHERE id = ?"
                        + " RETURNING id",
                Long.class, postId, revisionNo, sourceRevisionId);
    }

    /** 文章当前是否已发布（published_revision_id 非空）。 */
    public boolean isPublished(long postId) {
        Integer published = requireJdbc().query(
                "SELECT 1 FROM posts WHERE id = ? AND published_revision_id IS NOT NULL",
                rs -> rs.next() ? 1 : null, postId);
        return published != null;
    }

    /** 撤回发布：公开指针置空（归档 #22）。 */
    public void clearPublished(long postId) {
        requireJdbc().update(
                "UPDATE posts SET published_revision_id = NULL, updated_at = now() WHERE id = ?",
                postId);
    }

    /** 文章是否曾发布（first_published_at 非空；永久删除只允许从未发布的 Draft）。 */
    public boolean wasEverPublished(long postId) {
        Integer neverPublished = requireJdbc().query(
                "SELECT 1 FROM posts WHERE id = ? AND first_published_at IS NULL",
                rs -> rs.next() ? 1 : null, postId);
        return neverPublished == null;
    }

    /** 删除文章（仅从未发布的 Draft）；不存在时返回 false。 */
    public boolean deletePost(long postId) {
        return requireJdbc().update("DELETE FROM posts WHERE id = ?", postId) > 0;
    }

    /** slug 是否已被其他文章占用。 */
    public boolean slugConflict(String slug, long excludePostId) {
        Long conflict = requireJdbc().query(
                "SELECT id FROM posts WHERE slug = ? AND id <> ?",
                rs -> rs.next() ? rs.getLong("id") : null, slug, excludePostId);
        return conflict != null;
    }

    /** slug 是否已被历史重定向占用（#22：旧 slug 永久绑定其文章，不得复用）。 */
    public boolean redirectSlugConflict(String slug) {
        Long conflict = requireJdbc().query(
                "SELECT post_id FROM post_slug_redirects WHERE old_slug = ?",
                rs -> rs.next() ? rs.getLong("post_id") : null, slug);
        return conflict != null;
    }

    /** 管理端列表（含标签），按 (updated_at, id) 倒序。 */
    public List<AdminPostSummary> listAdminSummaries() {
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
                (RowCallbackHandler) rs -> {
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
        attachTagIds(jdbc, result.stream().map(s -> s.id).collect(java.util.stream.Collectors.toList()), (postId, tagId) -> {
            for (AdminPostSummary s : result) {
                if (s.id.equals(postId)) {
                    s.tagIds.add(tagId);
                    break;
                }
            }
        });
        return result;
    }

    /** 管理端详情（仅 Site Owner）：优先返回当前 Draft，否则返回 Published Revision。 */
    public AdminPostDetail getAdminDetail(long id) {
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
                (RowCallbackHandler) rs -> detail.tagIds.add(rs.getLong("tag_id")),
                id);
        return detail;
    }

    /** posts 行状态（保存 Draft / 发布共用读取）。 */
    public PostRow getPostState(long id) {
        return requireJdbc().query(
                "SELECT id, slug, category_id, draft_revision_id, published_revision_id,"
                        + " first_published_at FROM posts WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    PostRow row = new PostRow();
                    row.id = rs.getLong("id");
                    row.slug = rs.getString("slug");
                    long categoryId = rs.getLong("category_id");
                    row.categoryId = rs.wasNull() ? null : categoryId;
                    row.draftRevisionId = (Long) rs.getObject("draft_revision_id");
                    row.publishedRevisionId = (Long) rs.getObject("published_revision_id");
                    row.firstPublishedAt = rs.getObject("first_published_at", OffsetDateTime.class);
                    return row;
                },
                id);
    }

    /** 修订历史（#22）：全部不可变修订，标记当前已发布的那个。 */
    public List<RevisionItem> listRevisions(long id) {
        List<RevisionItem> result = new ArrayList<>();
        requireJdbc().query(
                "SELECT pr.id, pr.revision_no, pr.title, pr.summary, pr.created_at,"
                        + " (pr.id = p.published_revision_id) AS is_published"
                        + "  FROM post_revisions pr"
                        + "  JOIN posts p ON p.id = pr.post_id"
                        + " WHERE pr.post_id = ?"
                        + " ORDER BY pr.revision_no DESC",
                (RowCallbackHandler) rs -> {
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

    // ---- 公开端（PublicPostServiceImpl）----

    /** 已发布文章总数（categoryId / tagId 精确过滤，可同时使用取交集）。 */
    public int countPublished(Long categoryId, Long tagId) {
        StringBuilder where = publishedWhere(categoryId, tagId);
        Integer total = requireJdbc().queryForObject(
                "SELECT count(*) FROM posts p" + where, Integer.class,
                whereParams(categoryId, tagId).toArray());
        return total == null ? 0 : total;
    }

    /** 已发布文章分页列表（含标签），按最近发布时间倒序。 */
    public List<PublicPostSummary> listPublishedSummaries(Long categoryId, Long tagId,
                                                          int limit, long offset) {
        JdbcTemplate jdbc = requireJdbc();
        StringBuilder where = publishedWhere(categoryId, tagId);
        List<PublicPostSummary> items = new ArrayList<>();
        jdbc.query(
                "SELECT p.id, p.slug, p.last_published_at, p.category_id, c.name AS category_name,"
                        + " pr.title, pr.summary"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + where
                        + " ORDER BY p.last_published_at DESC, p.id DESC"
                        + " LIMIT ? OFFSET ?",
                (RowCallbackHandler) rs -> {
                    PublicPostSummary s = new PublicPostSummary();
                    s.id = rs.getLong("id");
                    s.slug = rs.getString("slug");
                    s.title = rs.getString("title");
                    s.summary = rs.getString("summary");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    s.publishedAt = publishedAt == null ? null : publishedAt.toString();
                    long categoryIdValue = rs.getLong("category_id");
                    s.categoryId = rs.wasNull() ? null : categoryIdValue;
                    s.categoryName = rs.getString("category_name");
                    s.tagIds = new ArrayList<>();
                    items.add(s);
                },
                appendParams(whereParams(categoryId, tagId), limit, offset));
        attachTagIds(jdbc, items.stream().map(s -> s.id).collect(java.util.stream.Collectors.toList()), (pid, tid) -> {
            for (PublicPostSummary s : items) {
                if (s.id.equals(pid)) {
                    s.tagIds.add(tid);
                    break;
                }
            }
        });
        return items;
    }

    /** 稳定 slug 对应的 Published Revision 详情（含标签）；不存在或未公开返回 null。 */
    public PublicPostDetail getPublishedBySlug(String slug) {
        JdbcTemplate jdbc = requireJdbc();
        PublicPostDetail detail = jdbc.query(
                "SELECT p.id, p.slug, p.last_published_at, p.category_id, c.name AS category_name,"
                        + " pr.title, pr.summary, pr.body_markdown"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + " WHERE p.slug = ? AND p.published_revision_id IS NOT NULL",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    PublicPostDetail d = new PublicPostDetail();
                    d.id = rs.getLong("id");
                    d.slug = rs.getString("slug");
                    d.title = rs.getString("title");
                    d.summary = rs.getString("summary");
                    d.bodyMarkdown = rs.getString("body_markdown");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    d.publishedAt = publishedAt == null ? null : publishedAt.toString();
                    long categoryIdValue = rs.getLong("category_id");
                    d.categoryId = rs.wasNull() ? null : categoryIdValue;
                    d.categoryName = rs.getString("category_name");
                    d.tagIds = new ArrayList<>();
                    return d;
                },
                slug);
        if (detail == null) {
            return null;
        }
        jdbc.query(
                "SELECT tag_id FROM post_tags WHERE post_id = ?",
                (RowCallbackHandler) rs -> detail.tagIds.add(rs.getLong("tag_id")),
                detail.id);
        return detail;
    }

    /** 历史 slug 解析（#22）：目标文章当前仍已发布时返回其当前 slug，否则 null。 */
    public String currentSlugForOldSlug(String oldSlug) {
        return requireJdbc().query(
                "SELECT p.slug FROM post_slug_redirects r"
                        + "  JOIN posts p ON p.id = r.post_id"
                        + " WHERE r.old_slug = ? AND p.published_revision_id IS NOT NULL",
                rs -> rs.next() ? rs.getString("slug") : null, oldSlug);
    }

    /** 搜索命中数（只覆盖标题与摘要投影）。 */
    public int searchCount(String like, Long categoryId, Long tagId) {
        List<Object> params = new ArrayList<>();
        params.add(like);
        params.add(like);
        params.addAll(whereParams(categoryId, tagId));
        StringBuilder where = new StringBuilder(
                " WHERE (d.title ILIKE ? OR d.summary ILIKE ?)"
                        + " AND p.published_revision_id IS NOT NULL");
        appendWhere(where, categoryId, tagId);
        Integer total = requireJdbc().queryForObject(
                "SELECT count(*) FROM post_search_documents d"
                        + "  JOIN posts p ON p.id = d.post_id" + where,
                Integer.class, params.toArray());
        return total == null ? 0 : total;
    }

    /** 搜索分页结果（含标签）：标题匹配 > 摘要匹配 > 最近发布时间。 */
    public List<PublicPostSummary> searchSummaries(String like, Long categoryId, Long tagId,
                                                   int limit, long offset) {
        JdbcTemplate jdbc = requireJdbc();
        List<Object> params = new ArrayList<>();
        params.add(like); // title_match
        params.add(like); // summary_match
        params.add(like); // WHERE title ILIKE
        params.add(like); // WHERE summary ILIKE
        params.addAll(whereParams(categoryId, tagId));
        StringBuilder where = new StringBuilder(
                " WHERE (d.title ILIKE ? OR d.summary ILIKE ?)"
                        + " AND p.published_revision_id IS NOT NULL");
        appendWhere(where, categoryId, tagId);
        List<PublicPostSummary> items = new ArrayList<>();
        jdbc.query(
                "SELECT p.id, p.slug, p.last_published_at, p.category_id, c.name AS category_name,"
                        + " d.title, d.summary,"
                        + " (d.title ILIKE ?) AS title_match,"
                        + " (d.summary ILIKE ?) AS summary_match"
                        + "  FROM post_search_documents d"
                        + "  JOIN posts p ON p.id = d.post_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + where
                        + " ORDER BY title_match DESC, summary_match DESC,"
                        + "          p.last_published_at DESC, p.id DESC"
                        + " LIMIT ? OFFSET ?",
                (RowCallbackHandler) rs -> {
                    PublicPostSummary s = new PublicPostSummary();
                    s.id = rs.getLong("id");
                    s.slug = rs.getString("slug");
                    s.title = rs.getString("title");
                    s.summary = rs.getString("summary");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    s.publishedAt = publishedAt == null ? null : publishedAt.toString();
                    long categoryIdValue = rs.getLong("category_id");
                    s.categoryId = rs.wasNull() ? null : categoryIdValue;
                    s.categoryName = rs.getString("category_name");
                    s.tagIds = new ArrayList<>();
                    items.add(s);
                },
                appendParams(params, limit, offset));
        attachTagIds(jdbc, items.stream().map(s -> s.id).collect(java.util.stream.Collectors.toList()), (pid, tid) -> {
            for (PublicPostSummary s : items) {
                if (s.id.equals(pid)) {
                    s.tagIds.add(tid);
                    break;
                }
            }
        });
        return items;
    }

    // ---- Feed（FeedServiceImpl）----

    /** RSS 最近 N 篇已发布文章（含 Category 与 Tag）。 */
    public List<FeedItem> listFeedItems(int limit) {
        JdbcTemplate jdbc = requireJdbc();
        List<FeedItem> items = new ArrayList<>();
        jdbc.query(
                "SELECT p.id AS post_id, p.slug, p.last_published_at, c.name AS category_name,"
                        + " pr.title, pr.summary"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + " WHERE p.published_revision_id IS NOT NULL"
                        + " ORDER BY p.last_published_at DESC, p.id DESC LIMIT ?",
                (RowCallbackHandler) rs -> {
                    FeedItem item = new FeedItem();
                    item.postId = rs.getLong("post_id");
                    item.slug = rs.getString("slug");
                    item.title = rs.getString("title");
                    item.summary = rs.getString("summary");
                    item.categoryName = rs.getString("category_name");
                    item.publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    item.tags = new ArrayList<>();
                    items.add(item);
                },
                limit);
        attachFeedTags(jdbc, items);
        return items;
    }

    /** Sitemap 全部已发布文章（slug、标题、最近发布时间）。 */
    public List<FeedItem> listSitemapItems() {
        List<FeedItem> items = new ArrayList<>();
        requireJdbc().query(
                "SELECT p.slug, p.last_published_at, pr.title"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + " WHERE p.published_revision_id IS NOT NULL"
                        + " ORDER BY p.last_published_at DESC, p.id DESC",
                (RowCallbackHandler) rs -> {
                    FeedItem item = new FeedItem();
                    item.slug = rs.getString("slug");
                    item.title = rs.getString("title");
                    item.publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    items.add(item);
                });
        return items;
    }

    // ---- 一次性导入（MvpContentImporter）----

    /** 写入导入文章（初始 Published Revision 指针由 setPublishedRevision 设置）。 */
    public Long insertImportedPost(String slug, long categoryId,
                                   OffsetDateTime firstPublishedAt, OffsetDateTime lastPublishedAt) {
        return requireJdbc().queryForObject(
                "INSERT INTO posts (slug, category_id, first_published_at, last_published_at)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, slug, categoryId, firstPublishedAt, lastPublishedAt);
    }

    /** 设置 Published Revision 指针。 */
    public void setPublishedRevision(long postId, long revisionId) {
        requireJdbc().update(
                "UPDATE posts SET published_revision_id = ? WHERE id = ?",
                revisionId, postId);
    }

    /** posts 行状态。 */
    public static class PostRow {

        public long id;
        public String slug;
        public Long categoryId;
        public Long draftRevisionId;
        public Long publishedRevisionId;
        public OffsetDateTime firstPublishedAt;
    }

    /** Feed 渲染行（RSS / Sitemap 共用）。 */
    public static class FeedItem {

        public long postId;
        public String slug;
        public String title;
        public String summary;
        public String categoryName;
        public OffsetDateTime publishedAt;
        public List<String> tags;
    }

    // ---- 内部 ----

    private static StringBuilder publishedWhere(Long categoryId, Long tagId) {
        StringBuilder where = new StringBuilder(" WHERE p.published_revision_id IS NOT NULL");
        appendWhere(where, categoryId, tagId);
        return where;
    }

    private static void appendWhere(StringBuilder where, Long categoryId, Long tagId) {
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
        }
        if (tagId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.id AND pt.tag_id = ?)");
        }
    }

    private static List<Object> whereParams(Long categoryId, Long tagId) {
        List<Object> params = new ArrayList<>();
        if (categoryId != null) {
            params.add(categoryId);
        }
        if (tagId != null) {
            params.add(tagId);
        }
        return params;
    }

    private static Object[] appendParams(List<Object> params, Object... extra) {
        List<Object> all = new ArrayList<>(params);
        Collections.addAll(all, extra);
        return all.toArray();
    }

    private void attachTagIds(JdbcTemplate jdbc, List<Long> postIds, TagConsumer consumer) {
        if (postIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(postIds.size(), "?"));
        jdbc.query(
                "SELECT post_id, tag_id FROM post_tags WHERE post_id IN (" + placeholders + ")",
                (RowCallbackHandler) rs -> consumer.accept(rs.getLong("post_id"), rs.getLong("tag_id")),
                postIds.toArray());
    }

    private void attachFeedTags(JdbcTemplate jdbc, List<FeedItem> items) {
        if (items.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(items.size(), "?"));
        jdbc.query(
                "SELECT pt.post_id, t.name FROM post_tags pt"
                        + "  JOIN tags t ON t.id = pt.tag_id"
                        + " WHERE pt.post_id IN (" + placeholders + ")",
                (RowCallbackHandler) rs -> {
                    long postId = rs.getLong("post_id");
                    for (FeedItem item : items) {
                        if (item.postId == postId) {
                            item.tags.add(rs.getString("name"));
                            break;
                        }
                    }
                },
                items.stream().map(i -> i.postId).toArray());
    }

    private interface TagConsumer {

        void accept(long postId, long tagId);
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Blog Post 服务不可用");
        }
        return jdbc;
    }
}
