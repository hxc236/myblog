package com.myblog.backend.service;

import com.myblog.backend.pojo.ProjectItem;

import java.util.List;

/**
 * Project 服务契约（#18）：内容库 CRUD、排序与首页精选。
 *
 * <p>实现见 {@link com.myblog.backend.service.impl.ProjectServiceImpl}。
 * 所有写操作在单一事务内完成；精选顺序在精选范围内唯一（数据库部分唯一
 * 索引为最终保证），首页精选数量恒为 0–3。
 */
public interface ProjectService {

    /** 数据库读路径是否可用。 */
    boolean isAvailable();

    /** 全部 Project（管理端），按 (display_order, id) 排序。 */
    List<ProjectItem> listProjects();

    /** 首页精选 Project（Visitor），按 featured_order 排序；无精选时为空列表。 */
    List<ProjectItem> listFeatured();

    /** 新建 Project（自动分配 display_order 并校验）。 */
    ProjectItem createProject(ProjectItem project);

    /** 更新 Project（含列表重排与精选槽位替换）；不存在时返回 null。 */
    ProjectItem updateProject(long id, ProjectItem project);

    /** 删除 Project；不存在时返回 false。 */
    boolean deleteProject(long id);
}
