package com.myblog.backend.pojo;

import java.util.List;

/** 精选作品（Project），字段契约见 #5 4.2。 */
public class Project {

    public String key;
    public String title;
    public String summary;
    public String role;
    public List<String> stack;
    public String year;
    public String repositoryUrl;
    public String demoUrl;
    public int featuredOrder;
}