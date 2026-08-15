package com.myblog.publicsite.imports;

import com.myblog.publicsite.content.ContentLoader;
import com.myblog.publicsite.content.Post;
import com.myblog.publicsite.content.PostMeta;
import com.myblog.publicsite.content.Project;
import com.myblog.publicsite.content.SkillGroup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * MVP 内容一次性导入器（#27）。
 *
 * <p>把随仓库发布的 JSON 与 Markdown（经 {@link ContentLoader} 同一套严格
 * 校验读取）写入 PostgreSQL：Public Introduction（五组技能）、作品区设置、
 * 联系方式、Project（含精选顺序）与 Blog Post（初始 Published Revision、
 * slug、发布时间；导入文章归入内置 Uncategorized，无 Tag）。不建立文件与
 * 数据库的双向同步；每个领域在已有数据时跳过（空库重复执行结果一致）。
 */
@Service
public class MvpContentImporter {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final ContentLoader contentLoader;

    public MvpContentImporter(ObjectProvider<JdbcTemplate> jdbcTemplate, ContentLoader contentLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.contentLoader = contentLoader;
    }

    public boolean isAvailable() {
        return jdbcTemplate.getIfAvailable() != null;
    }

    /** 导入结果摘要。 */
    public static class ImportSummary {

        public boolean introductionImported;
        public int projectsImported;
        public int postsImported;

        @Override
        public String toString() {
            return "ImportSummary{introduction=" + introductionImported
                    + ", projects=" + projectsImported + ", posts=" + postsImported + "}";
        }
    }

    @Transactional
    public ImportSummary importAll() {
        ImportSummary summary = new ImportSummary();
        JdbcTemplate jdbc = requireJdbc();
        summary.introductionImported = importIntroduction(jdbc);
        summary.projectsImported = importProjects(jdbc);
        summary.postsImported = importPosts(jdbc);
        if (summary.projectsImported + summary.postsImported > 0) {
            rebuildSearchIndex(jdbc);
        }
        return summary;
    }

    private boolean importIntroduction(JdbcTemplate jdbc) {
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM public_introduction LIMIT 1",
                rs -> rs.next() ? 1 : null);
        if (hasContent != null) {
            return false;
        }
        com.myblog.publicsite.content.Introduction intro = contentLoader.getIntroduction();
        jdbc.update(
                "INSERT INTO public_introduction (id, display_name, headline, introduction)"
                        + " VALUES (1, ?, ?, ?)",
                intro.displayName, intro.headline, intro.introduction);
        for (int g = 0; g < intro.skillGroups.size(); g++) {
            SkillGroup group = intro.skillGroups.get(g);
            jdbc.update(
                    "INSERT INTO skill_groups (name, position) VALUES (?, ?)",
                    group.name, g);
            for (int s = 0; s < group.skills.size(); s++) {
                jdbc.update(
                        "INSERT INTO skill_group_items (group_id, name, position)"
                                + " SELECT id, ?, ? FROM skill_groups WHERE position = ?",
                        group.skills.get(s), s, g);
            }
        }
        // 作品区设置与联系方式（#17 字段来自同一 introduction.json）
        jdbc.update(
                "INSERT INTO project_section_settings (id, title, subtitle) VALUES (1, ?, '')",
                "个人项目展示");
        jdbc.update(
                "INSERT INTO contact_settings (id, email, github_url, copyright)"
                        + " VALUES (1, ?, ?, ?)",
                intro.email, intro.githubUrl, intro.copyright);
        return true;
    }

    private int importProjects(JdbcTemplate jdbc) {
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM projects LIMIT 1",
                rs -> rs.next() ? 1 : null);
        if (hasContent != null) {
            return 0;
        }
        int count = 0;
        for (Project project : contentLoader.getProjects()) {
            Long id = jdbc.queryForObject(
                    "INSERT INTO projects (title, summary, role, year, repository_url, demo_url,"
                            + " display_order, featured_order)"
                            + " VALUES (?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?) RETURNING id",
                    Long.class, project.title, project.summary, project.role, project.year,
                    project.repositoryUrl, project.demoUrl,
                    project.featuredOrder - 1, project.featuredOrder);
            for (int s = 0; s < project.stack.size(); s++) {
                jdbc.update(
                        "INSERT INTO project_stack_items (project_id, name, position)"
                                + " VALUES (?, ?, ?)",
                        id, project.stack.get(s), s);
            }
            count++;
        }
        return count;
    }

    private int importPosts(JdbcTemplate jdbc) {
        Integer hasContent = jdbc.query(
                "SELECT 1 FROM posts LIMIT 1",
                rs -> rs.next() ? 1 : null);
        if (hasContent != null) {
            return 0;
        }
        Long uncategorizedId = jdbc.queryForObject(
                "SELECT id FROM categories WHERE is_uncategorized", Long.class);
        int count = 0;
        for (PostMeta meta : contentLoader.getPostsMeta()) {
            Post post = contentLoader.findPost(meta.slug)
                    .orElseThrow(() -> new IllegalStateException("缺少正文：" + meta.slug));
            OffsetDateTime publishedAt = LocalDate.parse(meta.publishedAt)
                    .atStartOfDay().atOffset(ZoneOffset.UTC);
            Long postId = jdbc.queryForObject(
                    "INSERT INTO posts (slug, category_id, first_published_at, last_published_at)"
                            + " VALUES (?, ?, ?, ?) RETURNING id",
                    Long.class, meta.slug, uncategorizedId, publishedAt, publishedAt);
            Long revisionId = jdbc.queryForObject(
                    "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                            + " VALUES (?, 1, ?, ?, ?) RETURNING id",
                    Long.class, postId, meta.title, meta.summary, post.body);
            jdbc.update(
                    "UPDATE posts SET published_revision_id = ? WHERE id = ?",
                    revisionId, postId);
            count++;
        }
        return count;
    }

    private void rebuildSearchIndex(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM post_search_documents");
        jdbc.update(
                "INSERT INTO post_search_documents (post_id, title, summary, updated_at)"
                        + " SELECT p.id, pr.title, pr.summary, p.last_published_at"
                        + "   FROM posts p"
                        + "   JOIN post_revisions pr ON pr.id = p.published_revision_id");
    }

    private JdbcTemplate requireJdbc() {
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("数据库未配置：导入器不可用");
        }
        return jdbc;
    }
}
