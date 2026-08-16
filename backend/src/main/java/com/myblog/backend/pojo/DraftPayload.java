package com.myblog.backend.pojo;

import java.util.List;

/** 草稿保存载荷（#20 编辑表单）。 */
public class DraftPayload {

    public String title;
    public String summary;
    public String bodyMarkdown;
    public String slug;
    public Long categoryId;
    public List<Long> tagIds;
}
