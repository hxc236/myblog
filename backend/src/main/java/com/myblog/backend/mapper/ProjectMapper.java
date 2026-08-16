package com.myblog.backend.mapper;

import com.myblog.backend.pojo.ProjectItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Project 数据访问（#18）：projects 与 project_stack_items。
 *
 * <p>精选顺序在精选范围内唯一由数据库部分唯一索引最终保证；列表重排与
 * 精选槽位替换的 SQL 都在本 mapper，业务校验由 service 层完成。
 */
@Component
public class ProjectMapper {

    private static final String PROJECT_COLUMNS =
            "SELECT id, title, summary, role, year, repository_url, demo_url,"
                    + " display_order, featured_order FROM projects";

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public ProjectMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 是否已有任何 Project（一次性导入判空用）。 */
    public boolean hasAnyProject() {
        JdbcTemplate jdbc = requireJdbc();
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM projects LIMIT 1",
                rs -> rs.next() ? 1 : null);
        return hasContent != null;
    }

    /** 全部 Project（管理端），按 (display_order, id) 排序。 */
    public List<ProjectItem> listProjects() {
        return queryProjects(" ORDER BY display_order, id");
    }

    /** 首页精选 Project（Visitor），按 featured_order 排序；无精选时为空列表。 */
    public List<ProjectItem> listFeatured() {
        return queryProjects(" WHERE featured_order IS NOT NULL ORDER BY featured_order");
    }

    /** 按 id 查找单个 Project（含 stack）；不存在时返回 null。 */
    public ProjectItem findProject(long id) {
        JdbcTemplate jdbc = requireJdbc();
        ProjectItem project = jdbc.query(
                PROJECT_COLUMNS + " WHERE id = ?",
                rs -> rs.next() ? readRow(rs) : null, id);
        if (project != null) {
            attachStack(jdbc, Collections.singletonMap(project.id, project));
        }
        return project;
    }

    /** 当前最大 display_order（无数据时为 -1）。 */
    public Integer maxDisplayOrder() {
        return requireJdbc().queryForObject(
                "SELECT COALESCE(MAX(display_order), -1) FROM projects", Integer.class);
    }

    /** 新建 Project，返回 id。 */
    public Long insertProject(String title, String summary, String role, String year,
                              String repositoryUrl, String demoUrl,
                              int displayOrder, Integer featuredOrder) {
        return requireJdbc().queryForObject(
                "INSERT INTO projects (title, summary, role, year, repository_url, demo_url,"
                        + " display_order, featured_order)"
                        + " VALUES (?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?)"
                        + " RETURNING id",
                Long.class,
                title, summary, role, year, repositoryUrl, demoUrl, displayOrder, featuredOrder);
    }

    /** 更新 Project 字段（不含 stack）。 */
    public void updateProject(long id, String title, String summary, String role, String year,
                              String repositoryUrl, String demoUrl,
                              int displayOrder, Integer featuredOrder) {
        requireJdbc().update(
                "UPDATE projects SET title = ?, summary = ?, role = ?, year = ?,"
                        + " repository_url = NULLIF(?, ''), demo_url = NULLIF(?, ''),"
                        + " display_order = ?, featured_order = ?, updated_at = now()"
                        + " WHERE id = ?",
                title, summary, role, year, repositoryUrl, demoUrl,
                displayOrder, featuredOrder, id);
    }

    /** 删除 Project；不存在时返回 false。 */
    public boolean deleteProject(long id) {
        return requireJdbc().update("DELETE FROM projects WHERE id = ?", id) > 0;
    }

    /** 整组替换 stack（先删后插，position 决定顺序）。 */
    public void replaceStack(long projectId, List<String> stack) {
        JdbcTemplate jdbc = requireJdbc();
        jdbc.update("DELETE FROM project_stack_items WHERE project_id = ?", projectId);
        for (int i = 0; i < stack.size(); i++) {
            jdbc.update(
                    "INSERT INTO project_stack_items (project_id, name, position) VALUES (?, ?, ?)",
                    projectId, stack.get(i), i);
        }
    }

    /** 列表重排（下移区间）：旧位置之后到新位置之间的项目平移 -1。 */
    public void shiftDisplayOrderDown(int oldOrder, int newOrder) {
        requireJdbc().update(
                "UPDATE projects SET display_order = display_order - 1"
                        + " WHERE display_order > ? AND display_order <= ?",
                oldOrder, newOrder);
    }

    /** 列表重排（上移区间）：新位置到旧位置之间的项目平移 +1。 */
    public void shiftDisplayOrderUp(int newOrder, int oldOrder) {
        requireJdbc().update(
                "UPDATE projects SET display_order = display_order + 1"
                        + " WHERE display_order >= ? AND display_order < ?",
                newOrder, oldOrder);
    }

    /** 精选槽位替换：同一槽位上的旧精选让位（事务内两步，保证 ≤3 且唯一）。 */
    public void clearFeaturedSlot(int featuredOrder, long excludeId) {
        requireJdbc().update(
                "UPDATE projects SET featured_order = NULL"
                        + " WHERE featured_order = ? AND id <> ?",
                featuredOrder, excludeId);
    }

    // ---- 内部 ----

    private List<ProjectItem> queryProjects(String tail) {
        JdbcTemplate jdbc = requireJdbc();
        Map<Long, ProjectItem> byId = new HashMap<>();
        List<ProjectItem> order = new ArrayList<>();
        jdbc.query(PROJECT_COLUMNS + tail, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            ProjectItem p = readRow(rs);
            p.stack = new ArrayList<>();
            byId.put(p.id, p);
            order.add(p);
        });
        if (!byId.isEmpty()) {
            attachStack(jdbc, byId);
        }
        return order;
    }

    private void attachStack(JdbcTemplate jdbc, Map<Long, ProjectItem> byId) {
        String placeholders = String.join(",", Collections.nCopies(byId.size(), "?"));
        jdbc.query(
                "SELECT project_id, name FROM project_stack_items"
                        + " WHERE project_id IN (" + placeholders + ")"
                        + " ORDER BY project_id, position, id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        byId.get(rs.getLong("project_id")).stack.add(rs.getString("name")),
                byId.keySet().toArray());
    }

    private ProjectItem readRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectItem p = new ProjectItem();
        p.stack = new ArrayList<>();
        p.id = rs.getLong("id");
        p.title = rs.getString("title");
        p.summary = rs.getString("summary");
        p.role = rs.getString("role");
        p.year = rs.getString("year");
        p.repositoryUrl = rs.getString("repository_url");
        p.demoUrl = rs.getString("demo_url");
        p.displayOrder = rs.getInt("display_order");
        int featured = rs.getInt("featured_order");
        p.featuredOrder = rs.wasNull() ? null : featured;
        return p;
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Project 服务不可用");
        }
        return jdbc;
    }
}
