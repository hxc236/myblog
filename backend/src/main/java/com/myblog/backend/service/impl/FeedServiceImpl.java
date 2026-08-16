package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.PostMapper;
import com.myblog.backend.service.FeedService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * RSS / Sitemap 生成服务实现（#26）：请求时从数据库生成，只包含当前公开内容。
 *
 * <p>可见性规则：只有 {@code published_revision_id} 非空的文章进入 RSS 与
 * Sitemap；归档（指针置空）后立即消失，恢复并重新发布后重新可发现。
 * RSS 不输出 Markdown 全文；Sitemap 不含 Draft、Archived Post 或 Admin
 * Console URL。数据访问见 {@link PostMapper}。
 */
@Service
public class FeedServiceImpl implements FeedService {

    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    private static final int RSS_LIMIT = 20;

    private final PostMapper mapper;

    public FeedServiceImpl(PostMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /**
     * RSS 2.0：最近二十篇 Published Revision 的标题、摘要、发布日期、
     * Category、Tag、永久链接与稳定 GUID（GUID = 永久链接）。
     */
    public String rss(String siteTitle, String baseUrl) {
        List<PostMapper.FeedItem> items = mapper.listFeedItems(RSS_LIMIT);

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
        for (PostMapper.FeedItem item : items) {
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
        List<PostMapper.FeedItem> items = mapper.listSitemapItems();

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, baseUrl + "/", null);
        appendUrl(xml, baseUrl + "/blog", null);
        for (PostMapper.FeedItem item : items) {
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
}
