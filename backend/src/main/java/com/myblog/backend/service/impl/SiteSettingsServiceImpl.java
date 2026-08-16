package com.myblog.backend.service.impl;
import com.myblog.backend.service.SiteIntroductionService;
import com.myblog.backend.service.SiteSettingsService;

import com.myblog.backend.pojo.SkillGroup;
import com.myblog.backend.pojo.SiteIntroduction;
import com.myblog.backend.pojo.SiteSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 站点设置服务（#17）：读取与“保存并发布”。
 *
 * <p>保存在同一事务中原子更新 Public Introduction、技能分组、作品区设置与
 * 联系方式；任何一步失败都会整体回滚，Visitor 始终看到最近一次完整发布的
 * 内容。管理端只能提交本服务固定接受的字段（隐私字段边界由 DTO 形状 +
 * 全局 strict JSON 反序列化共同保证）。
 */
@Service
public class SiteSettingsServiceImpl implements SiteSettingsService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final SiteIntroductionService introductionService;

    public SiteSettingsServiceImpl(
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            SiteIntroductionService introductionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.introductionService = introductionService;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 读取当前站点设置（编辑表单初始值）。 */
    public SiteSettings getSettings() {
        JdbcTemplate jdbc = requireJdbc();
        SiteSettings settings = new SiteSettings();

        SiteIntroduction current = introductionService.getIntroduction();
        SiteSettings.IntroductionSettings introduction = new SiteSettings.IntroductionSettings();
        introduction.displayName = current.displayName;
        introduction.headline = current.headline;
        introduction.introduction = current.introduction;
        introduction.skillGroups = new ArrayList<>();
        for (SkillGroup group : current.skillGroups) {
            SiteSettings.SkillGroupInput input = new SiteSettings.SkillGroupInput();
            input.name = group.name;
            input.skills = new ArrayList<>(group.skills);
            introduction.skillGroups.add(input);
        }
        settings.introduction = introduction;

        settings.workSection = jdbc.queryForObject(
                "SELECT title, subtitle FROM project_section_settings WHERE id = 1",
                (rs, rowNum) -> {
                    SiteSettings.WorkSectionSettings work = new SiteSettings.WorkSectionSettings();
                    work.title = rs.getString("title");
                    work.subtitle = rs.getString("subtitle");
                    return work;
                });
        settings.contact = jdbc.queryForObject(
                "SELECT email, github_url, copyright FROM contact_settings WHERE id = 1",
                (rs, rowNum) -> {
                    SiteSettings.ContactSettings contact = new SiteSettings.ContactSettings();
                    contact.email = rs.getString("email");
                    contact.githubUrl = rs.getString("github_url");
                    contact.copyright = rs.getString("copyright");
                    return contact;
                });
        return settings;
    }

    /**
     * 校验并原子保存站点设置。
     *
     * @throws IllegalArgumentException 校验失败（字段缺失、超长、重名等）
     */
    @Transactional
    public void saveSettings(SiteSettings settings) {
        validate(settings);
        JdbcTemplate jdbc = requireJdbc();

        jdbc.update(
                "UPDATE public_introduction"
                        + "   SET display_name = ?, headline = ?, introduction = ?, updated_at = now()"
                        + " WHERE id = 1",
                trim(settings.introduction.displayName),
                trim(settings.introduction.headline),
                settings.introduction.introduction.trim());

        jdbc.update("DELETE FROM skill_group_items");
        jdbc.update("DELETE FROM skill_groups");
        for (int g = 0; g < settings.introduction.skillGroups.size(); g++) {
            SiteSettings.SkillGroupInput group = settings.introduction.skillGroups.get(g);
            jdbc.update(
                    "INSERT INTO skill_groups (name, position) VALUES (?, ?)",
                    trim(group.name), g);
            for (int s = 0; s < group.skills.size(); s++) {
                jdbc.update(
                        "INSERT INTO skill_group_items (group_id, name, position)"
                                + " SELECT id, ?, ? FROM skill_groups WHERE position = ?",
                        trim(group.skills.get(s)), s, g);
            }
        }

        jdbc.update(
                "UPDATE project_section_settings SET title = ?, subtitle = ?, updated_at = now()"
                        + " WHERE id = 1",
                trim(settings.workSection.title), settings.workSection.subtitle == null ? "" : settings.workSection.subtitle.trim());
        jdbc.update(
                "UPDATE contact_settings SET email = ?, github_url = ?, copyright = ?, updated_at = now()"
                        + " WHERE id = 1",
                trim(settings.contact.email),
                trim(settings.contact.githubUrl),
                trim(settings.contact.copyright));
    }

    private void validate(SiteSettings settings) {
        if (settings == null || settings.introduction == null
                || settings.workSection == null || settings.contact == null) {
            throw new IllegalArgumentException("站点设置必须包含 introduction、workSection 与 contact");
        }
        SiteSettings.IntroductionSettings intro = settings.introduction;
        requireText(intro.displayName, 64, "displayName");
        requireText(intro.headline, 120, "headline");
        requireText(intro.introduction, 2000, "introduction");
        if (intro.skillGroups == null || intro.skillGroups.isEmpty()) {
            throw new IllegalArgumentException("skillGroups 至少需要一个技能分组");
        }
        if (intro.skillGroups.size() > 20) {
            throw new IllegalArgumentException("skillGroups 最多 20 个分组");
        }
        Set<String> groupNames = new HashSet<>();
        for (int g = 0; g < intro.skillGroups.size(); g++) {
            SiteSettings.SkillGroupInput group = intro.skillGroups.get(g);
            if (group == null || group.skills == null) {
                throw new IllegalArgumentException("skillGroups[" + g + "] 缺少 name 或 skills");
            }
            requireText(group.name, 64, "skillGroups[" + g + "].name");
            if (!groupNames.add(group.name.trim())) {
                throw new IllegalArgumentException("技能分组名称不能重复：" + group.name.trim());
            }
            if (group.skills.isEmpty()) {
                throw new IllegalArgumentException("技能分组 " + group.name.trim() + " 至少需要一个技术项");
            }
            if (group.skills.size() > 50) {
                throw new IllegalArgumentException("技能分组 " + group.name.trim() + " 最多 50 个技术项");
            }
            Set<String> skills = new HashSet<>();
            for (int s = 0; s < group.skills.size(); s++) {
                requireText(group.skills.get(s), 64, "skillGroups[" + g + "].skills[" + s + "]");
                if (!skills.add(group.skills.get(s).trim())) {
                    throw new IllegalArgumentException(
                            "技能分组 " + group.name.trim() + " 内技术项不能重复：" + group.skills.get(s).trim());
                }
            }
        }
        requireText(settings.workSection.title, 120, "workSection.title");
        if (settings.workSection.subtitle != null && settings.workSection.subtitle.length() > 500) {
            throw new IllegalArgumentException("workSection.subtitle 最多 500 字符");
        }
        requireText(settings.contact.email, 254, "contact.email");
        requireText(settings.contact.githubUrl, 500, "contact.githubUrl");
        requireText(settings.contact.copyright, 200, "contact.copyright");
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " 最多 " + maxLength + " 字符");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：站点设置不可用");
        }
        return jdbc;
    }
}