package com.myblog.publicsite.posts;

import java.util.List;

/** Blog Post 管理端摘要（#20）：列表用。 */
public class AdminPostSummary {

    public Long id;
    public String slug;
    public String title;
    /** draft | published | draft_published */
    public String state;
    public Long categoryId;
    public String categoryName;
    public List<Long> tagIds;
    public String publishedAt;
    public String updatedAt;
}
