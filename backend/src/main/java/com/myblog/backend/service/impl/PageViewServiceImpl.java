package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.PageViewMapper;
import com.myblog.backend.pojo.DailyCount;
import com.myblog.backend.pojo.TopPost;
import com.myblog.backend.service.PageViewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 匿名 Page View 服务实现（#25）。
 *
 * <p>聚合键只有 (post_id, day)：不保存原始 IP、完整 User-Agent、设备指纹或
 * 逐次访问事件，也不产出独立访客数或画像。每日明细保留二十四个月（上报时
 * 惰性清理），累计值长期保留。同一浏览器每日去重由浏览器本地标记完成。
 * 数据访问见 {@link PageViewMapper}。
 */
@Service
public class PageViewServiceImpl implements PageViewService {

    private static final int RETENTION_MONTHS = 24;

    private final PageViewMapper mapper;

    public PageViewServiceImpl(PageViewMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /**
     * 上报一次 Page View：仅当文章当前已发布；UPSERT 当日明细并累计。
     * 每日重复上报由浏览器本地标记限制，服务端不识别访客。
     *
     * @return 文章不存在或未发布时返回 false
     */
    @Transactional
    public boolean reportView(long postId) {
        if (!mapper.isPostPublished(postId)) {
            return false;
        }
        LocalDate today = LocalDate.now();
        mapper.upsertDaily(postId, today);
        mapper.upsertTotal(postId);
        mapper.purgeExpiredDaily(LocalDate.now().minusMonths(RETENTION_MONTHS));
        return true;
    }

    /** 全站累计 Page View。 */
    public long siteTotal() {
        return mapper.siteTotal();
    }

    /** 最近 N 天全站每日趋势（含零值日期，按天升序）。 */
    public List<DailyCount> siteTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        return fillMissingDays(mapper.siteTrend(start), start, days);
    }

    /** 访问量最高的十篇 Blog Post（含标题与 slug，无任何访客信息）。 */
    public List<TopPost> topPosts(int limit) {
        return mapper.topPosts(limit);
    }

    /** 单篇最近 N 天趋势（30/90）。 */
    public List<DailyCount> postTrend(long postId, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        return fillMissingDays(mapper.postTrend(postId, start), start, days);
    }

    private List<DailyCount> fillMissingDays(List<DailyCount> result, LocalDate start, int days) {
        java.util.Map<String, Long> byDay = new java.util.HashMap<>();
        for (DailyCount d : result) {
            byDay.put(d.day, d.count);
        }
        List<DailyCount> filled = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            DailyCount d = new DailyCount();
            d.day = day.toString();
            d.count = byDay.getOrDefault(d.day, 0L);
            filled.add(d);
        }
        return filled;
    }
}
