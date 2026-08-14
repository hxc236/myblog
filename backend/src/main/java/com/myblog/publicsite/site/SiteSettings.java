package com.myblog.publicsite.site;

import java.util.List;

/**
 * 站点设置组（#17）：Public Introduction、作品区设置、联系方式。
 *
 * <p>Admin Console 一次性编辑，点击“保存并发布”后在同一事务中原子生效；
 * 不建立 Draft 或修订历史（#14 实现决策）。
 */
public class SiteSettings {

    public IntroductionSettings introduction;
    public WorkSectionSettings workSection;
    public ContactSettings contact;

    /** Public Introduction：只允许规格允许的公开字段（#14 用户故事 4）。 */
    public static class IntroductionSettings {

        public String displayName;
        public String headline;
        public String introduction;
        public List<SkillGroupInput> skillGroups;
    }

    /** 技能分组：可增删、改名、排序；组内技术项同。 */
    public static class SkillGroupInput {

        public String name;
        public List<String> skills;
    }

    /** 作品区设置：标题必填；副标题允许为空（空值前台不渲染）。 */
    public static class WorkSectionSettings {

        public String title;
        public String subtitle;
    }

    /** 联系方式：公开邮箱、GitHub 链接、版权标识。 */
    public static class ContactSettings {

        public String email;
        public String githubUrl;
        public String copyright;
    }
}
