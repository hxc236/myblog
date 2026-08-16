package com.myblog.backend.service;

/**
 * RSS / Sitemap 生成服务契约（#26）：请求时从数据库生成，只包含当前公开内容。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.FeedServiceImpl}。可见性规则：
 * 只有 {@code published_revision_id} 非空的文章进入 RSS 与 Sitemap；归档
 * （指针置空）后立即消失，恢复并重新发布后重新可发现。RSS 不输出 Markdown
 * 全文；Sitemap 不含 Draft、Archived Post 或 Admin Console URL。
 */
public interface FeedService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /** RSS 2.0：最近二十篇 Published Revision 的标题、摘要、发布日期、Category、Tag 与永久链接。 */
    String rss(String siteTitle, String baseUrl);

    /** Sitemap：首页、博客列表与全部当前已发布文章的稳定 URL。 */
    String sitemap(String baseUrl);
}
