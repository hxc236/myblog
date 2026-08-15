package com.myblog.publicsite.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MVP 内容快照加载器（#30 迁移工具，非运行时内容服务）。
 *
 * <p>仅由一次性导入器 {@link MvpContentImporter} 按需构造，读取
 * {@code legacy-mvp-snapshot/content} 只读迁移快照并严格校验；应用启动与
 * 公开/管理 API 不再读取任何文件内容（PostgreSQL 是唯一运行时权威源）。
 */
public class ContentLoader {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 64;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final ResourceLoader resourceLoader;
    private final String contentLocation;

    private Introduction introduction;
    private List<Project> projects;
    private List<PostMeta> postMetaList;
    private Map<String, Post> postsBySlug;

    public ContentLoader(ResourceLoader resourceLoader, String contentLocation) {
        this.resourceLoader = resourceLoader;
        // createRelative 需要目录结尾带斜杠，否则会把最后一段当作文件名替换掉
        this.contentLocation = contentLocation.endsWith("/") ? contentLocation : contentLocation + "/";
        load();
    }

    private Resource contentRoot() {
        return resourceLoader.getResource(contentLocation);
    }

    private Resource child(String path) {
        try {
            return contentRoot().createRelative(path);
        } catch (IOException e) {
            throw new IllegalStateException("无法解析内容路径 " + path, e);
        }
    }

    private void load() {
        this.introduction = loadIntroduction();
        this.projects = loadProjects();
        this.postMetaList = loadPosts();
        validateSlugUniqueness();
        this.postsBySlug = loadBodies();
    }

    private Introduction loadIntroduction() {
        Introduction intro = readJson("introduction.json", Introduction.class);
        require(intro != null, "introduction.json");
        requireText(intro.displayName, "introduction.displayName");
        requireText(intro.headline, "introduction.headline");
        requireText(intro.introduction, "introduction.introduction");
        requireText(intro.email, "introduction.email");
        requireText(intro.githubUrl, "introduction.githubUrl");
        requireText(intro.copyright, "introduction.copyright");
        require(intro.skillGroups != null && !intro.skillGroups.isEmpty(), "introduction.skillGroups 不能为空");

        List<String> groupNames = new ArrayList<>();
        for (int i = 0; i < intro.skillGroups.size(); i++) {
            SkillGroup group = intro.skillGroups.get(i);
            require(group != null, "introduction.skillGroups[" + i + "] 不能为空");
            requireText(group.name, "introduction.skillGroups[" + i + "].name");
            require(!groupNames.contains(group.name),
                    "introduction.skillGroups.name 不能重复：「" + group.name + "」");
            groupNames.add(group.name);
            require(group.skills != null && !group.skills.isEmpty(),
                    "introduction.skillGroups[" + i + "].skills 不能为空");
            for (String skill : group.skills) {
                requireText(skill, "introduction.skillGroups[" + i + "].skills");
            }
        }
        return intro;
    }

    private List<Project> loadProjects() {
        List<Project> projects = readJsonList("projects.json", Project.class);
        require(projects.size() == 3, "projects.json 必须恰好包含三个作品（首发契约）");

        List<Integer> orders = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            String at = "projects[" + i + "]";
            requireText(p.key, at + ".key");
            requireText(p.title, at + ".title");
            requireText(p.summary, at + ".summary");
            requireText(p.role, at + ".role");
            require(p.stack != null && !p.stack.isEmpty(), at + ".stack 不能为空");
            for (String s : p.stack) {
                requireText(s, at + ".stack");
            }
            requireText(p.year, at + ".year");
            boolean hasRepo = p.repositoryUrl != null && !p.repositoryUrl.isBlank();
            boolean hasDemo = p.demoUrl != null && !p.demoUrl.isBlank();
            require(hasRepo || hasDemo, at + " 至少需要一个已验证外部目标（repositoryUrl 或 demoUrl）");
            orders.add(p.featuredOrder);
        }
        require(orders.stream().distinct().count() == 3
                        && orders.contains(1) && orders.contains(2) && orders.contains(3),
                "projects.json 的 featuredOrder 必须是 1、2、3 各一次");
        projects.sort(Comparator.comparingInt(p -> p.featuredOrder));
        return List.copyOf(projects);
    }

    private List<PostMeta> loadPosts() {
        List<PostMeta> metas = readJsonList("posts.json", PostMeta.class);
        require(!metas.isEmpty(), "posts.json 至少需要一篇博客文章");
        for (int i = 0; i < metas.size(); i++) {
            PostMeta m = metas.get(i);
            String at = "posts[" + i + "]";
            requireText(m.slug, at + ".slug");
            require(isValidSlug(m.slug), at + ".slug 不符合 slug 格式（小写字母、数字与连字符）");
            requireText(m.title, at + ".title");
            requireText(m.summary, at + ".summary");
            requireText(m.publishedAt, at + ".publishedAt");
            require(m.readingMinutes >= 1, at + ".readingMinutes 必须是正整数");
            requireText(m.bodyResource, at + ".bodyResource");
        }
        return metas;
    }

    private void validateSlugUniqueness() {
        List<String> slugs = new ArrayList<>();
        for (PostMeta m : postMetaList) {
            require(!slugs.contains(m.slug), "posts.json 存在重复 slug：" + m.slug);
            slugs.add(m.slug);
        }
    }

    private Map<String, Post> loadBodies() {
        Map<String, Post> bySlug = new LinkedHashMap<>();
        for (PostMeta m : postMetaList) {
            String body = readText(child(m.bodyResource), "正文资源 " + m.bodyResource);
            require(body != null && !body.isBlank(), "正文资源 " + m.bodyResource + " 不能为空");
            Post post = new Post();
            post.slug = m.slug;
            post.title = m.title;
            post.summary = m.summary;
            post.publishedAt = m.publishedAt;
            post.readingMinutes = m.readingMinutes;
            post.featured = m.featured;
            post.body = body;
            bySlug.put(m.slug, post);
        }
        return Map.copyOf(bySlug);
    }

    // ---- 对外只读访问 ----

    public Introduction getIntroduction() {
        return introduction;
    }

    public List<Project> getProjects() {
        return projects;
    }

    /** 按发布日期倒序的博客元数据，不含正文。 */
    public List<PostMeta> getPostsMeta() {
        List<PostMeta> sorted = new ArrayList<>(postMetaList);
        sorted.sort(Comparator.comparing((PostMeta m) -> m.publishedAt).reversed());
        return List.copyOf(sorted);
    }

    public Optional<Post> findPost(String slug) {
        if (!isValidSlug(slug)) {
            return Optional.empty();
        }
        return Optional.ofNullable(postsBySlug.get(slug));
    }

    private boolean isValidSlug(String slug) {
        return slug != null
                && slug.length() <= MAX_SLUG_LENGTH
                && SLUG_PATTERN.matcher(slug.toLowerCase(Locale.ROOT)).matches()
                && slug.equals(slug.toLowerCase(Locale.ROOT));
    }

    // ---- 读取辅助 ----

    private <T> T readJson(String path, Class<T> type) {
        Resource resource = child(path);
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取内容文件 " + resource.getDescription(), e);
        }
    }

    private <T> List<T> readJsonList(String path, Class<T> type) {
        Resource resource = child(path);
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取内容文件 " + resource.getDescription(), e);
        }
    }

    private String readText(Resource resource, String what) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取" + what + "：" + resource.getDescription(), e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("公开内容校验失败：" + message);
        }
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " 不能为空");
    }
}
