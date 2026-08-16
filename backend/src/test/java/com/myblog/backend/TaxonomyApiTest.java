package com.myblog.backend;

import com.myblog.backend.service.AdminSessionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #19 Category / Tag 管理：Uncategorized 保护、删除迁移/解绑、
 * 公开读取只暴露已发布相关内容（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaxonomyApiTest {

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

    // ---- Uncategorized ----

    @Test
    void uncategorizedCategoryIsBuiltInAndProtected() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("未分类"))
                .andExpect(jsonPath("$[0].uncategorized").value(true));

        long uncategorizedId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE is_uncategorized", Long.class);

        mockMvc.perform(delete("/api/admin/categories/" + uncategorizedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("不可删除")));
        mockMvc.perform(put("/api/admin/categories/" + uncategorizedId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("不可改名")));
    }

    // ---- Category CRUD ----

    @Test
    void categoryCrudAndDuplicateNameRejection() throws Exception {
        String token = adminToken();

        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                mockMvc.perform(post("/api/admin/categories")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"工程实践\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("工程实践"))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/admin/categories/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"工程实践（改名）\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("工程实践（改名）"));

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"未分类\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ---- Tag CRUD ----

    @Test
    void tagCrudWithAutoSlug() throws Exception {
        String token = adminToken();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                mockMvc.perform(post("/api/admin/tags")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"AI Agent\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.slug").value("ai-agent"))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/admin/tags/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI Agent 进阶\"}"))
                .andExpect(status().isOk())
                // 非 ASCII 名称的 slug 基座与现有标签冲突时自动追加后缀，保证唯一
                .andExpect(jsonPath("$.slug").value(
                        org.hamcrest.Matchers.not("ai-agent")));
    }

    // ---- 删除迁移与解绑 ----

    @Test
    void deletingInUseCategoryMigratesPostsToUncategorizedInSameTransaction() throws Exception {
        String token = adminToken();
        long categoryId = createCategory(token, "实验分类");
        long uncategorizedId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE is_uncategorized", Long.class);
        long postId = insertPost("migrate-me", categoryId);

        mockMvc.perform(delete("/api/admin/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Long migrated = jdbcTemplate.queryForObject(
                "SELECT category_id FROM posts WHERE id = ?", Long.class, postId);
        assertThat(migrated).isEqualTo(uncategorizedId);
        // 分类已删除，文章仍在（未丢失内容）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE id = ?", Integer.class, categoryId))
                .isZero();
    }

    @Test
    void deletingTagUnbindsAllPostsWithoutOrphanRelations() throws Exception {
        String token = adminToken();
        long tagId = createTag(token, "临时标签");
        long postA = insertPost("tag-post-a", null);
        long postB = insertPost("tag-post-b", null);
        jdbcTemplate.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?), (?, ?)",
                postA, tagId, postB, tagId);

        mockMvc.perform(delete("/api/admin/tags/" + tagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_tags WHERE tag_id = ?", Integer.class, tagId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE id IN (?, ?)", Integer.class, postA, postB))
                .isEqualTo(2);
    }

    // ---- 公开读取只暴露已发布相关内容 ----

    @Test
    void publicCategoriesAndTagsOnlyExposePublishedContent() throws Exception {
        String token = adminToken();
        long draftOnlyCategory = createCategory(token, "只有草稿的分类");
        long publishedCategory = createCategory(token, "已发布分类");
        long publishedTag = createTag(token, "已发布标签");
        long draftOnlyTag = createTag(token, "只有草稿的标签");

        // 一篇草稿（无 published_revision_id）+ 一篇已发布
        insertPostWithPublishState("draft-post", draftOnlyCategory, null, null, publishedTag);
        insertPostWithPublishState("published-post", publishedCategory, 100L, 200L, publishedTag);

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("已发布分类"));
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("已发布标签"));
    }

    // ---- 未授权 ----

    @Test
    void adminTaxonomyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/tags")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private long createCategory(String token, String name) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                mockMvc.perform(post("/api/admin/categories")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long createTag(String token, String name) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                mockMvc.perform(post("/api/admin/tags")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long insertPost(String slug, Long categoryId) {
        return insertPostWithPublishState(slug, categoryId, null, null, null);
    }

    private long insertPostWithPublishState(String slug, Long categoryId,
                                            Long draftRevisionId, Long publishedRevisionId,
                                            Long tagId) {
        Long postId = jdbcTemplate.queryForObject(
                "INSERT INTO posts (slug, category_id) VALUES (?, ?) RETURNING id",
                Long.class, slug, categoryId);
        // 修订指针必须是真实存在的 post_revisions 行（#20 起有外键约束）
        Long resolvedDraft = draftRevisionId == null ? null : revisionRow(postId, 1, slug + "-draft");
        Long resolvedPublished = publishedRevisionId == null
                ? null : revisionRow(postId, 2, slug + "-pub");
        jdbcTemplate.update(
                "UPDATE posts SET draft_revision_id = ?, published_revision_id = ? WHERE id = ?",
                resolvedDraft, resolvedPublished, postId);
        if (tagId != null) {
            jdbcTemplate.update(
                    "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId);
        }
        return postId;
    }

    private long revisionRow(long postId, int revisionNo, String title) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO post_revisions (post_id, revision_no, title, summary, body_markdown)"
                        + " VALUES (?, ?, ?, '', '') RETURNING id",
                Long.class, postId, revisionNo, title);
    }

    private String adminToken() throws Exception {
        String code = sessionService.createExchangeCode("hxc236");
        String body = mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("token").asText();
    }
}