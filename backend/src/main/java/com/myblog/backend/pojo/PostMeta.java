package com.myblog.backend.pojo;

/** 博客文章元数据（Blog Post metadata），字段契约见 #5 4.3。 */
public class PostMeta {

    public String slug;
    public String title;
    public String summary;
    public String publishedAt;
    public int readingMinutes;
    public boolean featured;
    public String bodyResource;
}