package com.myblog.backend.service;
import com.myblog.backend.pojo.ProjectItem;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Project 服务（#18）：内容库 CRUD、排序与首页精选。
 *
 * <p>所有写操作在单一事务内完成；精选顺序在精选范围内唯一（数据库部分
 * 唯一索引为最终保证），首页精选数量恒为 0–3。
 */
@Service
public class ProjectService {

    private static final String PROJECT_COLUMNS =
            "SELECT id, title, summary, role, year, repository_url, demo_url,"
                    + " display_order, featured_order FROM projects";

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public ProjectService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 全部 Project（管理端），按 (display_order, id) 排序。 */
    public List<ProjectItem> listProjects() {
        return queryProjects(" ORDER BY display_order, id");
    }

    /** 首页精选 Project（Visitor），按 featured_order 排序；无精选时为空列表。 */
    public List<ProjectItem> listFeatured() {
        return queryProjects(" WHERE featured_order IS NOT NULL ORDER BY featured_order");
    }

    @Transactional
    public ProjectItem createProject(ProjectItem project) {
        JdbcTemplate jdbc = requireJdbc();
        Integer displayOrder = project.displayOrder;
        if (displayOrder == null) {
            Integer max = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(display_order), -1) FROM projects", Integer.class);
            displayOrder = max + 1;
        }
        project.displayOrder = displayOrder;
        validate(project);
        Long id = jdbc.queryForObject(
                "INSERT INTO projects (title, summary, role, year, repository_url, demo_url,"
                        + " display_order, featured_order)"
                        + " VALUES (?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?)"
                        + " RETURNING id",
                Long.class,
                trim(project.title), trim(project.summary), trim(project.role), trim(project.year),
                project.repositoryUrl, project.demoUrl, displayOrder, project.featuredOrder);
        replaceStack(jdbc, id, project.stack);
        project.id = id;
        return project;
    }

    @Transactional
    public ProjectItem updateProject(long id, ProjectItem project) {
        JdbcTemplate jdbc = requireJdbc();
        ProjectItem current = findProject(jdbc, id);
        if (current == null) {
            return null;
        }
        validate(project);
        reorderDisplay(jdbc, current.displayOrder, project.displayOrder);
        if (project.featuredOrder != null) {
            // 精选槽位替换：同一槽位上的旧精选让位（事务内两步，保证 ≤3 且唯一）
            jdbc.update(
                    "UPDATE projects SET featured_order = NULL"
                            + " WHERE featured_order = ? AND id <> ?",
                    project.featuredOrder, id);
        }
        jdbc.update(
                "UPDATE projects SET title = ?, summary = ?, role = ?, year = ?,"
                        + " repository_url = NULLIF(?, ''), demo_url = NULLIF(?, ''),"
                        + " display_order = ?, featured_order = ?, updated_at = now()"
                        + " WHERE id = ?",
                trim(project.title), trim(project.summary), trim(project.role), trim(project.year),
                project.repositoryUrl, project.demoUrl,
                project.displayOrder, project.featuredOrder, id);
        replaceStack(jdbc, id, project.stack);
        project.id = id;
        return project;
    }

    @Transactional
    public boolean deleteProject(long id) {
        JdbcTemplate jdbc = requireJdbc();
        return jdbc.update("DELETE FROM projects WHERE id = ?", id) > 0;
    }

    // ---- 内部 ----

    private List<ProjectItem> queryProjects(String tail) {
        JdbcTemplate jdbc = requireJdbc();
        Map<Long, ProjectItem> byId = new HashMap<>();
        List<ProjectItem> order = new ArrayList<>();
        jdbc.query(PROJECT_COLUMNS + tail, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            ProjectItem p = new ProjectItem();
            p.id = rs.getLong("id");
            p.title = rs.getString("title");
            p.summary = rs.getString("summary");
            p.role = rs.getString("role");
            p.year = rs.getString("year");
            p.repositoryUrl = rs.getString("repository_url");
            p.demoUrl = rs.getString("demo_url");
            int displayOrder = rs.getInt("display_order");
            p.displayOrder = displayOrder;
            int featured = rs.getInt("featured_order");
            p.featuredOrder = rs.wasNull() ? null : featured;
            p.stack = new ArrayList<>();
            byId.put(p.id, p);
            order.add(p);
        });
        if (!byId.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(byId.size(), "?"));
            jdbc.query(
                    "SELECT project_id, name FROM project_stack_items"
                            + " WHERE project_id IN (" + placeholders + ")"
                            + " ORDER BY project_id, position, id",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            byId.get(rs.getLong("project_id")).stack.add(rs.getString("name")),
                    byId.keySet().toArray());
        }
        return order;
    }

    private ProjectItem findProject(JdbcTemplate jdbc, long id) {
        return jdbc.query(
                PROJECT_COLUMNS + " WHERE id = ?",
                rs -> rs.next() ? readRow(rs) : null, id);
    }

    private ProjectItem readRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProjectItem p = new ProjectItem();
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

    /** 列表重排：把项目从旧位置移到新位置，其余项目在事务内平移。 */
    private void reorderDisplay(JdbcTemplate jdbc, int oldOrder, int newOrder) {
        if (oldOrder == newOrder) {
            return;
        }
        if (newOrder > oldOrder) {
            jdbc.update(
                    "UPDATE projects SET display_order = display_order - 1"
                            + " WHERE display_order > ? AND display_order <= ?",
                    oldOrder, newOrder);
        } else {
            jdbc.update(
                    "UPDATE projects SET display_order = display_order + 1"
                            + " WHERE display_order >= ? AND display_order < ?",
                    newOrder, oldOrder);
        }
    }

    private void replaceStack(JdbcTemplate jdbc, long projectId, List<String> stack) {
        jdbc.update("DELETE FROM project_stack_items WHERE project_id = ?", projectId);
        for (int i = 0; i < stack.size(); i++) {
            jdbc.update(
                    "INSERT INTO project_stack_items (project_id, name, position) VALUES (?, ?, ?)",
                    projectId, trim(stack.get(i)), i);
        }
    }

    private void validate(ProjectItem p) {
        if (p == null) {
            throw new IllegalArgumentException("Project 不能为空");
        }
        requireText(p.title, 200, "title");
        requireText(p.summary, 2000, "summary");
        requireText(p.role, 200, "role");
        requireText(p.year, 20, "year");
        boolean hasRepo = p.repositoryUrl != null && !p.repositoryUrl.trim().isEmpty();
        boolean hasDemo = p.demoUrl != null && !p.demoUrl.trim().isEmpty();
        if (!hasRepo && !hasDemo) {
            throw new IllegalArgumentException("至少需要一个外部目标（repositoryUrl 或 demoUrl）");
        }
        if (hasRepo && p.repositoryUrl.trim().length() > 500) {
            throw new IllegalArgumentException("repositoryUrl 最多 500 字符");
        }
        if (hasDemo && p.demoUrl.trim().length() > 500) {
            throw new IllegalArgumentException("demoUrl 最多 500 字符");
        }
        if (p.stack == null || p.stack.isEmpty()) {
            throw new IllegalArgumentException("stack 至少需要一个技术栈项");
        }
        if (p.stack.size() > 50) {
            throw new IllegalArgumentException("stack 最多 50 项");
        }
        for (String s : p.stack) {
            requireText(s, 100, "stack 项");
        }
        if (p.displayOrder == null || p.displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder 必须是非负整数");
        }
        if (p.featuredOrder != null && (p.featuredOrder < 1 || p.featuredOrder > 3)) {
            throw new IllegalArgumentException("featuredOrder 必须是 1–3 或 null");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " 最多 " + maxLength + " 字符");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：Project 服务不可用");
        }
        return jdbc;
    }
}