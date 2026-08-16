package com.myblog.backend.service;

import java.util.List;

/**
 * 匿名 Page View 服务契约（#25）。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.PageViewServiceImpl}。
 * 聚合键只有 (post_id, day)：不保存原始 IP、完整 User-Agent、设备指纹或
 * 逐次访问事件，也不产出独立访客数或画像。每日明细保留二十四个月（上报时
 * 惰性清理），累计值长期保留。同一浏览器每日去重由浏览器本地标记完成。
 */
public interface PageViewService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /**
     * 上报一次 Page View：仅当文章当前已发布；UPSERT 当日明细并累计。
     *
     * @return 文章不存在或未发布时返回 false
     */
    boolean reportView(long postId);

    /** 全站累计 Page View。 */
    long siteTotal();

    /** 最近 N 天全站每日趋势（含零值日期，按天升序）。 */
    List<DailyCount> siteTrend(int days);

    /** 访问量最高的十篇 Blog Post（含标题与 slug，无任何访客信息）。 */
    List<TopPost> topPosts(int limit);

    /** 单篇最近 N 天趋势（30/90）。 */
    List<DailyCount> postTrend(long postId, int days);

    /** 单日聚合。 */
    class DailyCount {

        public String day;
        public Long count;
    }

    /** 热门文章条目（仅内容标识与累计值，无访客信息）。 */
    class TopPost {

        public Long postId;
        public String slug;
        public String title;
        public Long total;
    }
}
