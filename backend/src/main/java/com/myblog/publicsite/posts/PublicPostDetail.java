package com.myblog.publicsite.posts;

import java.util.List;

/** 公开 Blog Post 详情（#21）：稳定 slug 对应的 Published Revision。 */
public class PublicPostDetail {

    public Long id;
    public String slug;
    public String title;
    public String summary;
    public String bodyMarkdown;
    public String publishedAt;
    public Long categoryId;
    public String categoryName;
    public List<Long> tagIds;
}
