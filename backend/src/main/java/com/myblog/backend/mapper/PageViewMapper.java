package com.myblog.backend.mapper;

import com.myblog.backend.pojo.DailyCount;
import com.myblog.backend.pojo.TopPost;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 匿名 Page View 数据访问（#25）：page_view_daily 与 page_view_totals。
 *
 * <p>聚合键只有 (post_id, day)；不保存原始 IP、完整 User-Agent、设备指纹或
 * 逐次访问事件。每日明细保留二十四个月（上报时惰性清理），累计值长期保留。
 */
@Component
public class PageViewMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public PageViewMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 文章当前是否已发布（Page View 只统计已发布文章）。 */
    public boolean isPostPublished(long postId) {
        Integer published = requireJdbc().query(
                "SELECT 1 FROM posts WHERE id = ? AND published_revision_id IS NOT NULL",
                rs -> rs.next() ? 1 : null, postId);
        return published != null;
    }

    /** UPSERT 当日明细（同一浏览器每日去重由浏览器本地标记完成）。 */
    public void upsertDaily(long postId, LocalDate day) {
        requireJdbc().update(
                "INSERT INTO page_view_daily (post_id, day, count) VALUES (?, ?, 1)"
                        + " ON CONFLICT (post_id, day) DO UPDATE SET count = page_view_daily.count + 1",
                postId, day);
    }

    /** UPSERT 全站累计。 */
    public void upsertTotal(long postId) {
        requireJdbc().update(
                "INSERT INTO page_view_totals (post_id, total) VALUES (?, 1)"
                        + " ON CONFLICT (post_id) DO UPDATE SET total = page_view_totals.total + 1",
                postId);
    }

    /** 惰性清理超过保留期（二十四个月）的每日明细；累计值不受影响。 */
    public void purgeExpiredDaily(LocalDate before) {
        requireJdbc().update("DELETE FROM page_view_daily WHERE day < ?", before);
    }

    /** 全站累计 Page View。 */
    public long siteTotal() {
        Long total = requireJdbc().queryForObject(
                "SELECT COALESCE(SUM(total), 0) FROM page_view_totals", Long.class);
        return total == null ? 0 : total;
    }

    /** 最近 N 天全站每日趋势（含零值日期，按天升序；零值由 service 补齐）。 */
    public List<DailyCount> siteTrend(LocalDate start) {
        List<DailyCount> result = new ArrayList<>();
        requireJdbc().query(
                "SELECT day, SUM(count) AS count FROM page_view_daily"
                        + " WHERE day >= ? GROUP BY day ORDER BY day",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    DailyCount d = new DailyCount();
                    d.day = rs.getDate("day").toLocalDate().toString();
                    d.count = rs.getLong("count");
                    result.add(d);
                },
                start);
        return result;
    }

    /** 访问量最高的 N 篇 Blog Post（含标题与 slug，无任何访客信息）。 */
    public List<TopPost> topPosts(int limit) {
        List<TopPost> result = new ArrayList<>();
        requireJdbc().query(
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

    /** 单篇最近 N 天趋势（30/90；零值由 service 补齐）。 */
    public List<DailyCount> postTrend(long postId, LocalDate start) {
        List<DailyCount> result = new ArrayList<>();
        requireJdbc().query(
                "SELECT day, count FROM page_view_daily"
                        + " WHERE post_id = ? AND day >= ? ORDER BY day",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    DailyCount d = new DailyCount();
                    d.day = rs.getDate("day").toLocalDate().toString();
                    d.count = rs.getLong("count");
                    result.add(d);
                },
                postId, start);
        return result;
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Page View 服务不可用");
        }
        return jdbc;
    }
}
