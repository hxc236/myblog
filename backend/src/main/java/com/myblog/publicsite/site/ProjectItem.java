package com.myblog.publicsite.site;

import java.util.List;

/**
 * Project 契约（#18）：管理端 CRUD 与公开首页精选共用。
 *
 * <p>保存即发布，不建立服务端 Draft 或修订历史（#14 实现决策）。
 * {@code featuredOrder} 为 1–3 表示首页精选位置，{@code null} 表示不精选。
 */
public class ProjectItem {

    public Long id;
    public String title;
    public String summary;
    public String role;
    public String year;
    public List<String> stack;
    public String repositoryUrl;
    public String demoUrl;
    public Integer displayOrder;
    public Integer featuredOrder;
}
