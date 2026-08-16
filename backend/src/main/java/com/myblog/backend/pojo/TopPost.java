package com.myblog.backend.pojo;

/** 热门文章条目（#25）：仅内容标识与累计值，无访客信息。 */
public class TopPost {

    public Long postId;
    public String slug;
    public String title;
    public Long total;
}
