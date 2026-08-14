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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #21 Visitor 浏览 Published Revision：分页、过滤、详情、可见性边界与
 * ETag/no-cache 语义（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class PublicPostsApiTest {

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

    // ---- 分页与排序 ----

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "first-post", "第一篇", "摘要A", categoryId, null);
        publishPost(token, "second-post", "第二篇", "摘要B", categoryId, null);
        publishPost(token, "third-post", "第三篇", "摘要C", categoryId, null);

        mockMvc.perform(get("/api/posts").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].slug").value("third-post"))
                .andExpect(jsonPath("$.items[1].slug").value("second-post"));

        mockMvc.perform(get("/api/posts").param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("first-post"));
    }

    // ---- 精确过滤 ----

    @Test
    void listFiltersByCategoryAndTag() throws Exception {
        String token = adminToken();
        long engineering = ensureCategory("工程实践");
        long ai = ensureCategory("AI 应用");
        long agentTag = ensureTag("Agent");
        publishPost(token, "cat-a", "工程篇", "s", engineering, agentTag);
        publishPost(token, "cat-b", "AI 篇", "s", ai, null);
        publishPost(token, "cat-c", "工程无标签篇", "s", engineering, null);

        mockMvc.perform(get("/api/posts").param("category", String.valueOf(engineering)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/posts").param("tag", String.valueOf(agentTag)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("cat-a"));

        // 分类 + 标签取交集
        mockMvc.perform(get("/api/posts")
                        .param("category", String.valueOf(engineering))
                        .param("tag", String.valueOf(agentTag)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("cat-a"));
    }

    // ---- 详情与可见性边界 ----

    @Test
    void detailServesOnlyPublishedRevisionByStableSlug() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "stable-post", "已发布标题", "摘要", categoryId, null);

        mockMvc.perform(get("/api/posts/stable-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("已发布标题"))
                .andExpect(jsonPath("$.bodyMarkdown").value(
                        org.hamcrest.Matchers.containsString("正文")))
                .andExpect(jsonPath("$.slug").value("stable-post"));

        // 未知 slug、草稿 slug、未发布 slug 一律站内 404
        mockMvc.perform(get("/api/posts/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));

        long draftOnlyId = createPost(token);
        saveDraft(draftOnlyId, token, "草稿文章", "s", "# 草稿正文", "draft-only", categoryId, null);
        mockMvc.perform(get("/api/posts/draft-only"))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftEditsNeverLeakIntoPublicListOrDetail() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "leak-check", "公开标题", "公开摘要", categoryId, null);

        // 修改已发布文章（产生新 Draft），不发布
        saveDraft(postId, token, "草稿标题-绝密", "草稿摘要-绝密", "草稿正文-绝密",
                "leak-check", categoryId, null);

        mockMvc.perform(get("/api/posts/leak-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("公开标题"))
                .andExpect(jsonPath("$.bodyMarkdown").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("绝密"))));
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("公开标题"));
    }

    // ---- ETag + no-cache ----

    @Test
    void listAndDetailUseEtagWithNoCacheAndReturn304OnMatch() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "etag-post", "版本一", "s", categoryId, null);

        MvcResult first = mockMvc.perform(get("/api/posts/etag-post"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().string("ETag",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/posts/etag-post").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));

        // 内容变化 → ETag 变化 → 重新验证返回 200
        long postId = postIdBySlug("etag-post");
        saveDraft(postId, token, "版本一（改）", "s", "# 正文 etag-post", "etag-post",
                categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/etag-post").header("If-None-Match", etag))
                .andExpect(status().isOk());
    }

    // ---- 辅助 ----

    private long publishPost(String token, String slug, String title, String summary,
                             long categoryId, Long tagId) throws Exception {
        long postId = createPost(token);
        saveDraft(postId, token, title, summary, "# 正文 " + slug, slug, categoryId, tagId);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return postId;
    }

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

    private long postIdBySlug(String slug) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM posts WHERE slug = ?", Long.class, slug);
    }

    private long ensureCategory(String name) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM categories WHERE name = ?",
                rs -> rs.next() ? rs.getLong("id") : null, name);
        if (id == null) {
            id = jdbcTemplate.queryForObject(
                    "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, name);
        }
        return id;
    }

    private long ensureTag(String name) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM tags WHERE name = ?",
                rs -> rs.next() ? rs.getLong("id") : null, name);
        if (id == null) {
            id = jdbcTemplate.queryForObject(
                    "INSERT INTO tags (slug, name) VALUES (?, ?) RETURNING id",
                    Long.class, name.toLowerCase().replace(' ', '-'), name);
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
