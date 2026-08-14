package com.myblog.publicsite.content;

/** 博客文章响应：元数据 + Markdown 正文（扁平结构，见 #5 5. 公开 API 契约）。 */
public class Post {

    public String slug;
    public String title;
    public String summary;
    public String publishedAt;
    public int readingMinutes;
    public boolean featured;
    public String body;
}
