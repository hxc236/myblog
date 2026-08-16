package com.myblog.backend.mapper;

import com.myblog.backend.pojo.SiteSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 站点设置数据访问（#17）：project_section_settings 与 contact_settings。
 *
 * <p>技能分组表属于 Public Introduction 数据域（见 {@link SiteIntroductionMapper}）；
 * 本 mapper 只负责作品区设置与联系方式两行单例设置。
 */
@Component
public class SiteSettingsMapper {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    public SiteSettingsMapper(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 读取作品区设置（标题 + 可选副标题）。 */
    public SiteSettings.WorkSectionSettings getWorkSection() {
        return requireJdbc().queryForObject(
                "SELECT title, subtitle FROM project_section_settings WHERE id = 1",
                (rs, rowNum) -> {
                    SiteSettings.WorkSectionSettings work = new SiteSettings.WorkSectionSettings();
                    work.title = rs.getString("title");
                    work.subtitle = rs.getString("subtitle");
                    return work;
                });
    }

    /** 读取联系方式（公开邮箱、GitHub 链接、版权标识）。 */
    public SiteSettings.ContactSettings getContact() {
        return requireJdbc().queryForObject(
                "SELECT email, github_url, copyright FROM contact_settings WHERE id = 1",
                (rs, rowNum) -> {
                    SiteSettings.ContactSettings contact = new SiteSettings.ContactSettings();
                    contact.email = rs.getString("email");
                    contact.githubUrl = rs.getString("github_url");
                    contact.copyright = rs.getString("copyright");
                    return contact;
                });
    }

    /** 更新作品区设置（保存并发布）。 */
    public void updateWorkSection(String title, String subtitle) {
        requireJdbc().update(
                "UPDATE project_section_settings SET title = ?, subtitle = ?, updated_at = now()"
                        + " WHERE id = 1",
                title, subtitle);
    }

    /** 更新联系方式（保存并发布）。 */
    public void updateContact(String email, String githubUrl, String copyright) {
        requireJdbc().update(
                "UPDATE contact_settings SET email = ?, github_url = ?, copyright = ?, updated_at = now()"
                        + " WHERE id = 1",
                email, githubUrl, copyright);
    }

    /** 写入初始作品区设置（一次性导入）。 */
    public void insertProjectSectionSettings(String title, String subtitle) {
        requireJdbc().update(
                "INSERT INTO project_section_settings (id, title, subtitle) VALUES (1, ?, ?)",
                title, subtitle);
    }

    /** 写入初始联系方式（一次性导入）。 */
    public void insertContactSettings(String email, String githubUrl, String copyright) {
        requireJdbc().update(
                "INSERT INTO contact_settings (id, email, github_url, copyright)"
                        + " VALUES (1, ?, ?, ?)",
                email, githubUrl, copyright);
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：站点设置不可用");
        }
        return jdbc;
    }
}
