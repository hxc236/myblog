package com.myblog.backend.utils;

import com.myblog.backend.mapper.PostMapper;
import com.myblog.backend.mapper.ProjectMapper;
import com.myblog.backend.mapper.SiteIntroductionMapper;
import com.myblog.backend.mapper.SiteSettingsMapper;
import com.myblog.backend.mapper.TaxonomyMapper;
import com.myblog.backend.pojo.Introduction;
import com.myblog.backend.pojo.Post;
import com.myblog.backend.pojo.PostMeta;
import com.myblog.backend.pojo.Project;
import com.myblog.backend.pojo.SkillGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * MVP 内容一次性导入器（#27/#30 迁移工具，非运行时内容服务）。
 *
 * <p>按需读取 {@code legacy-mvp-snapshot/content} 只读迁移快照（经
 * {@link ContentLoader} 严格校验）写入 PostgreSQL：Public Introduction
 * （五组技能）、作品区设置、联系方式、Project（含精选顺序）与 Blog Post
 * （初始 Published Revision、slug、发布时间；导入文章归入内置
 * Uncategorized）。不建立文件与数据库的双向同步；每个领域在已有数据时
 * 跳过。应用启动与公开/管理 API 不读取任何文件。数据库写入统一走
 * mapper 层。
 */
@Service
public class MvpContentImporter {

    private final SiteIntroductionMapper introductionMapper;
    private final SiteSettingsMapper settingsMapper;
    private final ProjectMapper projectMapper;
    private final PostMapper postMapper;
    private final TaxonomyMapper taxonomyMapper;
    private final String snapshotLocation;

    public MvpContentImporter(
            SiteIntroductionMapper introductionMapper,
            SiteSettingsMapper settingsMapper,
            ProjectMapper projectMapper,
            PostMapper postMapper,
            TaxonomyMapper taxonomyMapper,
            @Value("${mvp-import.content-location:classpath:legacy-mvp-snapshot/content}") String snapshotLocation) {
        this.introductionMapper = introductionMapper;
        this.settingsMapper = settingsMapper;
        this.projectMapper = projectMapper;
        this.postMapper = postMapper;
        this.taxonomyMapper = taxonomyMapper;
        this.snapshotLocation = snapshotLocation;
    }

    /** 按需构造快照加载器（仅导入时读取文件）。 */
    private ContentLoader snapshotLoader() {
        return new ContentLoader(new DefaultResourceLoader(), snapshotLocation);
    }

    public boolean isAvailable() {
        return postMapper.isAvailable();
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
        ContentLoader contentLoader = snapshotLoader();
        summary.introductionImported = importIntroduction(contentLoader);
        summary.projectsImported = importProjects(contentLoader);
        summary.postsImported = importPosts(contentLoader);
        if (summary.projectsImported + summary.postsImported > 0) {
            postMapper.rebuildSearchIndex();
        }
        return summary;
    }

    private boolean importIntroduction(ContentLoader contentLoader) {
        if (introductionMapper.hasAnyIntroduction()) {
            return false;
        }
        Introduction intro = contentLoader.getIntroduction();
        introductionMapper.insertIntroduction(
                intro.displayName, intro.headline, intro.introduction);
        for (int g = 0; g < intro.skillGroups.size(); g++) {
            SkillGroup group = intro.skillGroups.get(g);
            introductionMapper.insertSkillGroup(group.name, g);
            for (int s = 0; s < group.skills.size(); s++) {
                introductionMapper.insertSkillGroupItemByGroupPosition(
                        group.skills.get(s), s, g);
            }
        }
        // 作品区设置与联系方式（#17 字段来自同一 introduction.json）
        settingsMapper.insertProjectSectionSettings("个人项目展示", "");
        settingsMapper.insertContactSettings(intro.email, intro.githubUrl, intro.copyright);
        return true;
    }

    private int importProjects(ContentLoader contentLoader) {
        if (projectMapper.hasAnyProject()) {
            return 0;
        }
        int count = 0;
        for (Project project : contentLoader.getProjects()) {
            Long id = projectMapper.insertProject(
                    project.title, project.summary, project.role, project.year,
                    project.repositoryUrl, project.demoUrl,
                    project.featuredOrder - 1, project.featuredOrder);
            projectMapper.replaceStack(id, project.stack);
            count++;
        }
        return count;
    }

    private int importPosts(ContentLoader contentLoader) {
        if (postMapper.hasAnyPost()) {
            return 0;
        }
        Long uncategorizedId = taxonomyMapper.uncategorizedId();
        int count = 0;
        for (PostMeta meta : contentLoader.getPostsMeta()) {
            Post post = contentLoader.findPost(meta.slug)
                    .orElseThrow(() -> new IllegalStateException("缺少正文：" + meta.slug));
            OffsetDateTime publishedAt = LocalDate.parse(meta.publishedAt)
                    .atStartOfDay().atOffset(ZoneOffset.UTC);
            Long postId = postMapper.insertImportedPost(
                    meta.slug, uncategorizedId, publishedAt, publishedAt);
            Long revisionId = postMapper.insertRevision(
                    postId, 1, meta.title, meta.summary, post.body);
            postMapper.setPublishedRevision(postId, revisionId);
            count++;
        }
        return count;
    }
}
