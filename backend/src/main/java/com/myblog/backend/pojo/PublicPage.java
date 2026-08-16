package com.myblog.backend.pojo;

import java.util.List;

/** 公开 Blog Post 分页结果。 */
public class PublicPage {

    public List<PublicPostSummary> items;
    public int page;
    public int pageSize;
    public int total;
}
