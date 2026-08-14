package com.myblog.publicsite.posts;

import java.util.List;

/** 公开 Blog Post 列表项（#21）：只含 Published Revision 内容。 */
public class PublicPostSummary {

    public Long id;
    public String slug;
    public String title;
    public String summary;
    public String publishedAt;
    public Long categoryId;
    public String categoryName;
    public List<Long> tagIds;
}
