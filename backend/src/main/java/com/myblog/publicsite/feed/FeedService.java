package com.myblog.publicsite.feed;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RSS / Sitemap 生成（#26）：请求时从数据库生成，只包含当前公开内容。
 *
 * <p>可见性规则：只有 {@code published_revision_id} 非空的文章进入 RSS 与
 * Sitemap；归档（指针置空）后立即消失，恢复并重新发布后重新可发现。
 * RSS 不输出 Markdown 全文；Sitemap 不含 Draft、Archived Post 或 Admin
 * Console URL。
 */
@Service
public class FeedService {

    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    private static final int RSS_LIMIT = 20;

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public FeedService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /**
     * RSS 2.0：最近二十篇 Published Revision 的标题、摘要、发布日期、
     * Category、Tag、永久链接与稳定 GUID（GUID = 永久链接）。
     */
    public String rss(String siteTitle, String baseUrl) {
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
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    FeedItem item = new FeedItem();
                    item.postId = rs.getLong("post_id");
                    item.slug = rs.getString("slug");
                    item.title = rs.getString("title");
                    item.summary = rs.getString("summary");
                    item.categoryName = rs.getString("category_name");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    item.publishedAt = publishedAt == null ? null : publishedAt;
                    item.tags = new ArrayList<>();
                    items.add(item);
                },
                RSS_LIMIT);
        if (!items.isEmpty()) {
            String placeholders = String.join(",",
                    java.util.Collections.nCopies(items.size(), "?"));
            jdbc.query(
                    "SELECT pt.post_id, t.name FROM post_tags pt"
                            + "  JOIN tags t ON t.id = pt.tag_id"
                            + " WHERE pt.post_id IN (" + placeholders + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
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

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\"><channel>\n");
        xml.append("<title>").append(escape(siteTitle)).append("</title>\n");
        xml.append("<link>").append(escape(baseUrl)).append("</link>\n");
        xml.append("<description>").append(escape(siteTitle)).append(" 的公开内容订阅</description>\n");
        if (!items.isEmpty() && items.get(0).publishedAt != null) {
            xml.append("<lastBuildDate>").append(formatRfc1123(items.get(0).publishedAt))
                    .append("</lastBuildDate>\n");
        }
        for (FeedItem item : items) {
            String link = baseUrl + "/blog/" + item.slug;
            xml.append("<item>\n");
            xml.append("<title>").append(escape(item.title)).append("</title>\n");
            xml.append("<link>").append(escape(link)).append("</link>\n");
            xml.append("<guid isPermaLink=\"true\">").append(escape(link)).append("</guid>\n");
            if (item.summary != null && !item.summary.isEmpty()) {
                xml.append("<description>").append(escape(item.summary)).append("</description>\n");
            }
            if (item.publishedAt != null) {
                xml.append("<pubDate>").append(formatRfc1123(item.publishedAt)).append("</pubDate>\n");
            }
            if (item.categoryName != null && !item.categoryName.isEmpty()) {
                xml.append("<category>").append(escape(item.categoryName)).append("</category>\n");
            }
            for (String tag : item.tags) {
                xml.append("<category>").append(escape(tag)).append("</category>\n");
            }
            xml.append("</item>\n");
        }
        xml.append("</channel></rss>");
        return xml.toString();
    }

    /** Sitemap：首页、博客列表与全部当前已发布文章的稳定 URL。 */
    public String sitemap(String baseUrl) {
        JdbcTemplate jdbc = requireJdbc();
        List<FeedItem> items = new ArrayList<>();
        jdbc.query(
                "SELECT p.slug, p.last_published_at, pr.title"
                        + "  FROM posts p"
                        + "  JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + " WHERE p.published_revision_id IS NOT NULL"
                        + " ORDER BY p.last_published_at DESC, p.id DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    FeedItem item = new FeedItem();
                    item.slug = rs.getString("slug");
                    item.title = rs.getString("title");
                    OffsetDateTime publishedAt = rs.getObject("last_published_at", OffsetDateTime.class);
                    item.publishedAt = publishedAt;
                    items.add(item);
                });

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, baseUrl + "/", null);
        appendUrl(xml, baseUrl + "/blog", null);
        for (FeedItem item : items) {
            appendUrl(xml, baseUrl + "/blog/" + item.slug, item.publishedAt);
        }
        xml.append("</urlset>");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String url, OffsetDateTime lastmod) {
        xml.append("<url>\n");
        xml.append("<loc>").append(escape(url)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod.toLocalDate()).append("</lastmod>\n");
        }
        xml.append("</url>\n");
    }

    private static String formatRfc1123(OffsetDateTime time) {
        return RFC_1123.format(time.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Feed 服务不可用");
        }
        return jdbc;
    }

    private static class FeedItem {

        long postId;
        String slug;
        String title;
        String summary;
        String categoryName;
        OffsetDateTime publishedAt;
        List<String> tags;
    }
}
