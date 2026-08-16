package com.myblog.backend.pojo;

import java.util.List;

/** Blog Post 管理端详情（#20）：编辑表单与预览用（仅 Site Owner 可访问）。 */
public class AdminPostDetail {

    public Long id;
    public String slug;
    public String title;
    public String summary;
    public String bodyMarkdown;
    /** draft | published | draft_published */
    public String state;
    public Long categoryId;
    public String categoryName;
    public List<Long> tagIds;
    public String publishedAt;
    public String updatedAt;
}