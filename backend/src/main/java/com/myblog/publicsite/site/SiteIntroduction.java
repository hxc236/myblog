package com.myblog.publicsite.site;

import com.myblog.publicsite.content.SkillGroup;

import java.util.List;

/**
 * Public Introduction 正式领域 API 契约（#14 用户故事 4/5/6/7）。
 *
 * <p>只包含规格允许公开的字段：公开称呼、Hero 主标题、隐私安全个人介绍与
 * 可排序技能分组；联系方式（邮箱、GitHub、版权）属于后续 #17 的联系方式
 * 设置，不在此响应中出现。
 */
public class SiteIntroduction {

    public String displayName;
    public String headline;
    public String introduction;
    public List<SkillGroup> skillGroups;
}
