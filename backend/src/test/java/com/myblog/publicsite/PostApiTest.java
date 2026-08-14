package com.myblog.publicsite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.publicsite.admin.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #20 Blog Post Draft 生命周期：创建、保存、预览、发布事务、
 * Published Revision 不可变（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class PostApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mysite")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- 创建与保存 Draft ----

    @Test
    void createPostEntersDraftAndCanBeSavedWithCategoryAndTags() throws Exception {
        String token = adminToken();
        long categoryId = categoryId("工程实践");
        long tagId = tagId("Agent");

        long postId = createPost(token);
        mockMvc.perform(get("/api/admin/posts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId))
                .andExpect(jsonPath("$[0].state").value("draft"));

        String body = "{\"title\":\"第一篇\",\"summary\":\"摘要\","
                + "\"bodyMarkdown\":\"# 标题\\n正文内容\","
                + "\"slug\":\"first-post\",\"categoryId\":" + categoryId
                + ",\"tagIds\":[" + tagId + "]}";
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一篇"))
                .andExpect(jsonPath("$.bodyMarkdown").value(org.hamcrest.Matchers.containsString("正文内容")))
                .andExpect(jsonPath("$.slug").value("first-post"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.tagIds[0]").value(tagId))
                .andExpect(jsonPath("$.state").value("draft"));
    }

    @Test
    void invalidSlugOrDuplicateSlugIsRejectedAtSave() throws Exception {
        String token = adminToken();
        long categoryId = categoryId("工程实践");
        long postA = createPost(token);
        long postB = createPost(token);
        String valid = "{\"title\":\"t\",\"categoryId\":" + categoryId
                + ",\"bodyMarkdown\":\"\",\"slug\":\"shared-slug\"}";
        mockMvc.perform(put("/api/admin/posts/" + postA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk());

        // 重复 slug → 400
        mockMvc.perform(put("/api/admin/posts/" + postB)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        // 非法格式 → 400
        mockMvc.perform(put("/api/admin/posts/" + postB)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"categoryId\":" + categoryId
                                + ",\"bodyMarkdown\":\"\",\"slug\":\"Bad Slug!\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- 发布事务 ----

    @Test
    void publishRequiresCategoryAndAtomicallyUpdatesPointersAndSearchProjection()
            throws Exception {
        String token = adminToken();
        long categoryId = categoryId("工程实践");
        long postId = createPost(token);
        saveDraft(postId, token, "可发布", "摘要", "# 正文", "publishable", categoryId, null);

        // 未选分类 → 400
        jdbcTemplate.update("UPDATE posts SET category_id = NULL WHERE id = ?", postId);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("分类")));

        // 恢复分类后再发布 → 事务原子生效
        saveDraft(postId, token, "可发布", "摘要", "# 正文", "publishable", categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("published"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        Long publishedRevisionId = jdbcTemplate.queryForObject(
                "SELECT published_revision_id FROM posts WHERE id = ?", Long.class, postId);
        assertThat(publishedRevisionId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision_id FROM posts WHERE id = ?", Long.class, postId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT first_published_at IS NOT NULL AND last_published_at IS NOT NULL"
                        + " FROM posts WHERE id = ?", Boolean.class, postId))
                .isTrue();
        // 搜索投影已写入
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post_search_documents WHERE post_id = ?",
                String.class, postId))
                .isEqualTo("可发布");
    }

    @Test
    void editingPublishedPostKeepsPublishedRevisionImmutableUntilNextPublish() throws Exception {
        String token = adminToken();
        long categoryId = categoryId("工程实践");
        long postId = createPost(token);
        saveDraft(postId, token, "第一版标题", "第一版摘要", "第一版正文", "stable", categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long firstRevisionId = jdbcTemplate.queryForObject(
                "SELECT published_revision_id FROM posts WHERE id = ?", Long.class, postId);

        // 修改已发布文章 → 产生新 Draft，公开版本保持不变
        saveDraft(postId, token, "第二版标题", "第二版摘要", "第二版正文", "stable", categoryId, null);
        mockMvc.perform(get("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第二版标题"))
                .andExpect(jsonPath("$.state").value("draft_published"));

        // Published Revision 未变：指针、内容、搜索投影都保持第一版
        assertThat(jdbcTemplate.queryForObject(
                "SELECT published_revision_id FROM posts WHERE id = ?", Long.class, postId))
                .isEqualTo(firstRevisionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post_revisions WHERE id = ?", String.class, firstRevisionId))
                .isEqualTo("第一版标题");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post_search_documents WHERE post_id = ?",
                String.class, postId))
                .isEqualTo("第一版标题");

        // 再次发布 → 指针与投影切换到第二版；第一版修订内容仍不可变
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("published"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT published_revision_id FROM posts WHERE id = ?", Long.class, postId))
                .isNotEqualTo(firstRevisionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post_search_documents WHERE post_id = ?",
                String.class, postId))
                .isEqualTo("第二版标题");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM post_revisions WHERE id = ?", String.class, firstRevisionId))
                .isEqualTo("第一版标题");
    }

    @Test
    void publishWithoutDraftIsRejected() throws Exception {
        String token = adminToken();
        long postId = createPost(token);
        // 直接删除草稿修订制造“无 Draft”状态（正常情况下保存后才发布）
        jdbcTemplate.update("UPDATE posts SET draft_revision_id = NULL WHERE id = ?", postId);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("没有可发布的 Draft")));
    }

    // ---- 未授权 ----

    @Test
    void adminPostApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/posts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/posts/1/publish")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private long createPost(String token) throws Exception {
        JsonNode node = MAPPER.readTree(
                mockMvc.perform(post("/api/admin/posts")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private void saveDraft(long postId, String token, String title, String summary,
                           String markdown, String slug, Long categoryId, Long tagId)
            throws Exception {
        String tags = tagId == null ? "[]" : "[" + tagId + "]";
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"summary\":\"" + summary + "\","
                                + "\"bodyMarkdown\":\"" + markdown + "\",\"slug\":\"" + slug + "\","
                                + "\"categoryId\":" + categoryId + ",\"tagIds\":" + tags + "}"))
                .andExpect(status().isOk());
    }

    private long categoryId(String name) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM categories WHERE name = ?",
                rs -> rs.next() ? rs.getLong("id") : null, name);
        if (id == null) {
            id = jdbcTemplate.queryForObject(
                    "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, name);
        }
        return id;
    }

    private long tagId(String name) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM tags WHERE name = ?",
                rs -> rs.next() ? rs.getLong("id") : null, name);
        if (id == null) {
            id = jdbcTemplate.queryForObject(
                    "INSERT INTO tags (slug, name) VALUES (?, ?) RETURNING id",
                    Long.class, "tag-" + System.nanoTime(), name);
        }
        return id;
    }

    private String adminToken() throws Exception {
        String code = sessionService.createExchangeCode("hxc236");
        String body = mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("token").asText();
    }
}
