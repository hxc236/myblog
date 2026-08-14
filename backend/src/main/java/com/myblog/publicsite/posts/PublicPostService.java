package com.myblog.publicsite.posts;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 公开 Blog Post 读取服务（#21）：只读 Published Revision。
 *
 * <p>可见性边界：只有 {@code published_revision_id} 非空的文章可读，内容
 * 只来自该指针指向的不可变修订——Draft、旧修订、未发布与归档文章（#22 起
 * 归档即置空指针）都不会出现在列表或详情中。
 */
@Service
public class PublicPostService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 64;
    private static final int MAX_PAGE_SIZE = 50;

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public PublicPostService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /**
     * 分页列表：按最近发布时间倒序；categoryId / tagId 为精确过滤条件
     * （可同时使用，取交集）。
     */
    public PublicPage listPublished(int page, int pageSize, Long categoryId, Long tagId) {
        JdbcTemplate jdbc = requireJdbc();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE p.published_revision_id IS NOT NULL");
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
            params.add(categoryId);
        }
        if (tagId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.id AND pt.tag_id = ?)");
            params.add(tagId);
        }

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM posts p" + where, Integer.class, params.toArray());
        String order = " ORDER BY p.last_published_at DESC, p.id DESC";
        List<PublicPostSummary> items = new ArrayList<>();
        jdbc.query(
                "SELECT p.id, p.slug, p.last_published_at, p.category_id, c.name AS category_name,"
                        + " pr.title, pr.summary"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN categories c ON c.id = p.category_id"
                        + where + order
                        + " LIMIT ? OFFSET ?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
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
                appendParams(params, safeSize, (long) (safePage - 1) * safeSize));
        attachTags(jdbc, items);
        PublicPage result = new PublicPage();
        result.items = items;
        result.page = safePage;
        result.pageSize = safeSize;
        result.total = total == null ? 0 : total;
        return result;
    }

    /** 稳定 slug 对应的 Published Revision；不存在或未公开返回 null。 */
    public PublicPostDetail getPublishedBySlug(String slug) {
        if (slug == null || slug.length() > MAX_SLUG_LENGTH
                || !SLUG_PATTERN.matcher(slug.toLowerCase(Locale.ROOT)).matches()) {
            return null;
        }
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
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        detail.tagIds.add(rs.getLong("tag_id")),
                detail.id);
        return detail;
    }

    private void attachTags(JdbcTemplate jdbc, List<PublicPostSummary> items) {
        if (items.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(items.size(), "?"));
        jdbc.query(
                "SELECT post_id, tag_id FROM post_tags WHERE post_id IN (" + placeholders + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    long postId = rs.getLong("post_id");
                    for (PublicPostSummary s : items) {
                        if (s.id.equals(postId)) {
                            s.tagIds.add(rs.getLong("tag_id"));
                            break;
                        }
                    }
                },
                items.stream().map(s -> s.id).toArray());
    }

    private static Object[] appendParams(List<Object> params, Object... extra) {
        List<Object> all = new ArrayList<>(params);
        java.util.Collections.addAll(all, extra);
        return all.toArray();
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：公开 Blog Post 服务不可用");
        }
        return jdbc;
    }

    /** 分页结果。 */
    public static class PublicPage {

        public List<PublicPostSummary> items;
        public int page;
        public int pageSize;
        public int total;
    }
}
