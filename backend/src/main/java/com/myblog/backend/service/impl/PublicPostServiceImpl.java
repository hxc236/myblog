package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.PostMapper;
import com.myblog.backend.pojo.PublicPage;
import com.myblog.backend.pojo.PublicPostDetail;
import com.myblog.backend.pojo.ResolvedSlug;
import com.myblog.backend.service.PublicPostService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 公开 Blog Post 读取服务实现（#21）：只读 Published Revision。
 *
 * <p>可见性边界：只有 {@code published_revision_id} 非空的文章可读，内容
 * 只来自该指针指向的不可变修订——Draft、旧修订、未发布与归档文章（#22 起
 * 归档即置空指针）都不会出现在列表或详情中。数据访问见 {@link PostMapper}。
 */
@Service
public class PublicPostServiceImpl implements PublicPostService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 64;
    private static final int MAX_PAGE_SIZE = 50;

    private final PostMapper mapper;

    public PublicPostServiceImpl(PostMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /**
     * 分页列表：按最近发布时间倒序；categoryId / tagId 为精确过滤条件
     * （可同时使用，取交集）。
     */
    public PublicPage listPublished(int page, int pageSize, Long categoryId, Long tagId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        int total = mapper.countPublished(categoryId, tagId);
        PublicPage result = new PublicPage();
        result.items = mapper.listPublishedSummaries(
                categoryId, tagId, safeSize, (long) (safePage - 1) * safeSize);
        result.page = safePage;
        result.pageSize = safeSize;
        result.total = total;
        return result;
    }

    /** 稳定 slug 对应的 Published Revision；不存在或未公开返回 null。 */
    public PublicPostDetail getPublishedBySlug(String slug) {
        if (!isValidSlug(slug)) {
            return null;
        }
        return mapper.getPublishedBySlug(slug);
    }

    /**
     * slug 解析（#22）：当前已发布 slug 直接命中；历史 slug 命中且目标文章
     * 当前仍已发布时返回重定向目标；归档文章的任意 slug 返回 null（站内 404）。
     */
    public ResolvedSlug resolvePublishedSlug(String slug) {
        if (!isValidSlug(slug)) {
            return null;
        }
        PublicPostDetail detail = mapper.getPublishedBySlug(slug);
        if (detail != null) {
            ResolvedSlug resolved = new ResolvedSlug();
            resolved.detail = detail;
            return resolved;
        }
        String currentSlug = mapper.currentSlugForOldSlug(slug);
        if (currentSlug != null) {
            ResolvedSlug resolved = new ResolvedSlug();
            resolved.redirectToSlug = currentSlug;
            return resolved;
        }
        return null;
    }

    /**
     * 搜索（#23）：只覆盖标题与摘要（post_search_documents 投影），1–50 字符。
     * 1–2 字符使用有结果上限（20）的 ILIKE；3 字符及以上由 pg_trgm GIN 索引
     * 加速（上限 50）。排序：标题匹配 > 摘要匹配 > 最近发布时间。
     */
    public PublicPage searchPublished(String q, int page, int pageSize,
                                      Long categoryId, Long tagId) {
        if (q == null || q.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索词不能为空");
        }
        String query = q.trim();
        if (query.length() > 50) {
            throw new IllegalArgumentException("搜索词最多 50 个字符");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String like = "%" + query + "%";
        int resultLimit = query.length() <= 2 ? 20 : 50;

        int total = mapper.searchCount(like, categoryId, tagId);
        PublicPage result = new PublicPage();
        result.items = mapper.searchSummaries(
                like, categoryId, tagId, resultLimit, (long) (safePage - 1) * safeSize);
        result.page = safePage;
        result.pageSize = safeSize;
        result.total = Math.min(total, resultLimit);
        return result;
    }

    private boolean isValidSlug(String slug) {
        return slug != null
                && slug.length() <= MAX_SLUG_LENGTH
                && SLUG_PATTERN.matcher(slug.toLowerCase(Locale.ROOT)).matches();
    }
}
