package com.myblog.backend.service.impl;

import com.myblog.backend.mapper.ProjectMapper;
import com.myblog.backend.pojo.ProjectItem;
import com.myblog.backend.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Project 服务实现（#18）：内容库 CRUD、排序与首页精选。
 *
 * <p>所有写操作在单一事务内完成；精选顺序在精选范围内唯一（数据库部分
 * 唯一索引为最终保证），首页精选数量恒为 0–3。数据访问见
 * {@link ProjectMapper}。
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper mapper;

    public ProjectServiceImpl(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAvailable() {
        return mapper.isAvailable();
    }

    /** 全部 Project（管理端），按 (display_order, id) 排序。 */
    public List<ProjectItem> listProjects() {
        return mapper.listProjects();
    }

    /** 首页精选 Project（Visitor），按 featured_order 排序；无精选时为空列表。 */
    public List<ProjectItem> listFeatured() {
        return mapper.listFeatured();
    }

    @Transactional
    public ProjectItem createProject(ProjectItem project) {
        Integer displayOrder = project.displayOrder;
        if (displayOrder == null) {
            displayOrder = mapper.maxDisplayOrder() + 1;
        }
        project.displayOrder = displayOrder;
        validate(project);
        Long id = mapper.insertProject(
                trim(project.title), trim(project.summary), trim(project.role), trim(project.year),
                project.repositoryUrl, project.demoUrl, displayOrder, project.featuredOrder);
        mapper.replaceStack(id, trimAll(project.stack));
        project.id = id;
        return project;
    }

    @Transactional
    public ProjectItem updateProject(long id, ProjectItem project) {
        ProjectItem current = mapper.findProject(id);
        if (current == null) {
            return null;
        }
        validate(project);
        reorderDisplay(current.displayOrder, project.displayOrder);
        if (project.featuredOrder != null) {
            // 精选槽位替换：同一槽位上的旧精选让位（事务内两步，保证 ≤3 且唯一）
            mapper.clearFeaturedSlot(project.featuredOrder, id);
        }
        mapper.updateProject(
                id,
                trim(project.title), trim(project.summary), trim(project.role), trim(project.year),
                project.repositoryUrl, project.demoUrl,
                project.displayOrder, project.featuredOrder);
        mapper.replaceStack(id, trimAll(project.stack));
        project.id = id;
        return project;
    }

    @Transactional
    public boolean deleteProject(long id) {
        return mapper.deleteProject(id);
    }

    // ---- 内部 ----

    /** 列表重排：把项目从旧位置移到新位置，其余项目在事务内平移。 */
    private void reorderDisplay(int oldOrder, int newOrder) {
        if (oldOrder == newOrder) {
            return;
        }
        if (newOrder > oldOrder) {
            mapper.shiftDisplayOrderDown(oldOrder, newOrder);
        } else {
            mapper.shiftDisplayOrderUp(newOrder, oldOrder);
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

    private static List<String> trimAll(List<String> stack) {
        List<String> trimmed = new ArrayList<>(stack.size());
        for (String s : stack) {
            trimmed.add(trim(s));
        }
        return trimmed;
    }
}
