package com.myblog.backend.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 匿名 Page View 服务（#25）。
 *
 * <p>聚合键只有 (post_id, day)：不保存原始 IP、完整 User-Agent、设备指纹或
 * 逐次访问事件，也不产出独立访客数或画像。每日明细保留二十四个月（上报时
 * 惰性清理），累计值长期保留。同一浏览器每日去重由浏览器本地标记完成。
 */
@Service
public class PageViewService {

    private static final int RETENTION_MONTHS = 24;

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public PageViewService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /**
     * 上报一次 Page View：仅当文章当前已发布；UPSERT 当日明细并累计。
     * 每日重复上报由浏览器本地标记限制，服务端不识别访客。
     *
     * @return 文章不存在或未发布时返回 false
     */
    @Transactional
    public boolean reportView(long postId) {
        JdbcTemplate jdbc = requireJdbc();
        Integer published = jdbc.query(
                "SELECT 1 FROM posts WHERE id = ? AND published_revision_id IS NOT NULL",
                rs -> rs.next() ? 1 : null, postId);
        if (published == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        jdbc.update(
                "INSERT INTO page_view_daily (post_id, day, count) VALUES (?, ?, 1)"
                        + " ON CONFLICT (post_id, day) DO UPDATE SET count = page_view_daily.count + 1",
                postId, today);
        jdbc.update(
                "INSERT INTO page_view_totals (post_id, total) VALUES (?, 1)"
                        + " ON CONFLICT (post_id) DO UPDATE SET total = page_view_totals.total + 1",
                postId);
        purgeExpired(jdbc);
        return true;
    }

    /** 每日明细保留二十四个月（惰性清理，累计值不受影响）。 */
    private void purgeExpired(JdbcTemplate jdbc) {
        jdbc.update(
                "DELETE FROM page_view_daily WHERE day < ?",
                LocalDate.now().minusMonths(RETENTION_MONTHS));
    }

    /** 全站累计 Page View。 */
    public long siteTotal() {
        JdbcTemplate jdbc = requireJdbc();
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total), 0) FROM page_view_totals", Long.class);
        return total == null ? 0 : total;
    }

    /** 最近 N 天全站每日趋势（含零值日期，按天升序）。 */
    public List<DailyCount> siteTrend(int days) {
        JdbcTemplate jdbc = requireJdbc();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<DailyCount> result = new ArrayList<>();
        jdbc.query(
                "SELECT day, SUM(count) AS count FROM page_view_daily"
                        + " WHERE day >= ? GROUP BY day ORDER BY day",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    DailyCount d = new DailyCount();
                    d.day = rs.getDate("day").toLocalDate().toString();
                    d.count = rs.getLong("count");
                    result.add(d);
                },
                start);
        return fillMissingDays(result, start, days);
    }

    /** 访问量最高的十篇 Blog Post（含标题与 slug，无任何访客信息）。 */
    public List<TopPost> topPosts(int limit) {
        JdbcTemplate jdbc = requireJdbc();
        List<TopPost> result = new ArrayList<>();
        jdbc.query(
                "SELECT t.post_id, p.slug, t.total,"
                        + " COALESCE(dr.title, pr.title, latest.title, '') AS title"
                        + "  FROM page_view_totals t"
                        + "  JOIN posts p ON p.id = t.post_id"
                        + "  LEFT JOIN post_revisions dr ON dr.id = p.draft_revision_id"
                        + "  LEFT JOIN post_revisions pr ON pr.id = p.published_revision_id"
                        + "  LEFT JOIN LATERAL (SELECT title FROM post_revisions lr"
                        + "    WHERE lr.post_id = p.id ORDER BY lr.revision_no DESC LIMIT 1) latest ON true"
                        + " ORDER BY t.total DESC, p.id DESC LIMIT ?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    TopPost t = new TopPost();
                    t.postId = rs.getLong("post_id");
                    t.slug = rs.getString("slug");
                    t.title = rs.getString("title");
                    t.total = rs.getLong("total");
                    result.add(t);
                },
                limit);
        return result;
    }

    /** 单篇最近 N 天趋势（30/90）。 */
    public List<DailyCount> postTrend(long postId, int days) {
        JdbcTemplate jdbc = requireJdbc();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<DailyCount> result = new ArrayList<>();
        jdbc.query(
                "SELECT day, count FROM page_view_daily"
                        + " WHERE post_id = ? AND day >= ? ORDER BY day",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    DailyCount d = new DailyCount();
                    d.day = rs.getDate("day").toLocalDate().toString();
                    d.count = rs.getLong("count");
                    result.add(d);
                },
                postId, start);
        return fillMissingDays(result, start, days);
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

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Page View 服务不可用");
        }
        return jdbc;
    }

    /** 单日聚合。 */
    public static class DailyCount {

        public String day;
        public Long count;
    }

    /** 热门文章条目（仅内容标识与累计值，无访客信息）。 */
    public static class TopPost {

        public Long postId;
        public String slug;
        public String title;
        public Long total;
    }
}