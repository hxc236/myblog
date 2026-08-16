package com.myblog.backend.service;

import com.myblog.backend.pojo.PublicPostDetail;
import com.myblog.backend.pojo.PublicPostSummary;

import java.util.List;

/**
 * 公开 Blog Post 读取服务契约（#21）：只读 Published Revision。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.PublicPostServiceImpl}。
 * 可见性边界：只有 {@code published_revision_id} 非空的文章可读，内容只来自
 * 该指针指向的不可变修订——Draft、旧修订、未发布与归档文章（#22 起归档即
 * 置空指针）都不会出现在列表或详情中。
 */
public interface PublicPostService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /**
     * 分页列表：按最近发布时间倒序；categoryId / tagId 为精确过滤条件
     * （可同时使用，取交集）。
     */
    PublicPage listPublished(int page, int pageSize, Long categoryId, Long tagId);

    /** 稳定 slug 对应的 Published Revision；不存在或未公开返回 null。 */
    PublicPostDetail getPublishedBySlug(String slug);

    /** slug 解析（#22）：当前已发布 slug 直接命中；历史 slug 命中且目标文章仍已发布时返回重定向目标。 */
    ResolvedSlug resolvePublishedSlug(String slug);

    /** 搜索（#23）：只覆盖标题与摘要（post_search_documents 投影），1–50 字符。 */
    PublicPage searchPublished(String q, int page, int pageSize, Long categoryId, Long tagId);

    /** slug 解析结果：命中详情或 301 重定向目标（二选一）。 */
    class ResolvedSlug {

        public PublicPostDetail detail;
        public String redirectToSlug;
    }

    /** 分页结果。 */
    class PublicPage {

        public List<PublicPostSummary> items;
        public int page;
        public int pageSize;
        public int total;
    }
}
