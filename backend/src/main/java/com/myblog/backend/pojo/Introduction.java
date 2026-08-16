package com.myblog.backend.pojo;

import java.util.List;

/** 公开介绍（Public Introduction），字段契约见 #5 4.1。 */
public class Introduction {

    public String displayName;
    public String headline;
    public String introduction;
    public List<SkillGroup> skillGroups;
    public String email;
    public String githubUrl;
    public String copyright;
}