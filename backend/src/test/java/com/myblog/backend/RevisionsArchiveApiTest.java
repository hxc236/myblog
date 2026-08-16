package com.myblog.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #22 修订、slug 重定向与归档恢复（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@Transactional
class RevisionsArchiveApiTest {

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

    // ---- slug 重定向 ----

    @Test
    void slugChangeCreatesPermanent301AndConflictsAreRejected() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "old-name", "标题", categoryId);

        // 修改已发布文章的 slug
        saveDraft(postId, token, "标题", "s", "# 正文", "new-name", categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 旧 slug → 永久 301 到当前 slug
        mockMvc.perform(get("/api/posts/old-name"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/api/posts/new-name"));
        mockMvc.perform(get("/api/posts/new-name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("new-name"));

        // 历史重定向占用 → 新文章不能复用
        long otherPost = createPost(token);
        mockMvc.perform(put("/api/admin/posts/" + otherPost)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"categoryId\":" + categoryId
                                + ",\"bodyMarkdown\":\"\",\"slug\":\"old-name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("历史重定向")));
    }

    // ---- 归档 ----

    @Test
    void archiveHidesCurrentAndHistoricalSlugsAndRemovesSearchProjection() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "to-archive", "标题", categoryId);
        saveDraft(postId, token, "标题", "s", "# 正文", "renamed-archive", categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_search_documents WHERE post_id = ?",
                Integer.class, postId)).isEqualTo(1);

        mockMvc.perform(post("/api/admin/posts/" + postId + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("archived"));

        // 当前与历史 slug 都返回站内 404
        mockMvc.perform(get("/api/posts/renamed-archive"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/to-archive"))
                .andExpect(status().isNotFound());
        // 搜索投影已移除
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_search_documents WHERE post_id = ?",
                Integer.class, postId)).isZero();
        // 修订历史保留
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_revisions WHERE post_id = ?", Integer.class, postId))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void archiveOnlyAppliesToCurrentlyPublishedPosts() throws Exception {
        String token = adminToken();
        long draftPost = createPost(token);
        mockMvc.perform(post("/api/admin/posts/" + draftPost + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("只能归档")));
    }

    // ---- 修订历史与恢复 ----

    @Test
    void revisionsListMarksPublishedAndRestoreCopiesHistoricalRevisionToNewDraft()
            throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "rev-post", "第一版", categoryId);
        saveDraft(postId, token, "第二版", "s2", "# 第二版正文", "rev-post", categoryId, null);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 修订历史：两条，最新一条标记为已发布
        mockMvc.perform(get("/api/admin/posts/" + postId + "/revisions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("第二版"))
                .andExpect(jsonPath("$[0].published").value(true))
                .andExpect(jsonPath("$[1].published").value(false));

        // 恢复第一版为 Draft：不直接覆盖线上内容
        long firstRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM post_revisions WHERE post_id = ? AND revision_no = 1",
                Long.class, postId);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":" + firstRevisionId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一版"))
                .andExpect(jsonPath("$.state").value("draft_published"));

        // 公开内容仍是第二版
        mockMvc.perform(get("/api/posts/rev-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第二版"));

        // 预览后再次发布 → 公开内容回到第一版
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("published"));
        mockMvc.perform(get("/api/posts/rev-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一版"));
    }

    // ---- 删除边界 ----

    @Test
    void onlyNeverPublishedDraftsCanBeHardDeleted() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");

        // 从未发布 → 可删除
        long draftPost = createPost(token);
        saveDraft(draftPost, token, "草稿", "s", "# 正文", "draft-delete", categoryId, null);
        mockMvc.perform(delete("/api/admin/posts/" + draftPost)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE id = ?", Integer.class, draftPost))
                .isZero();

        // 已发布 → 只能归档
        long publishedPost = publishPost(token, "keep-me", "标题", categoryId);
        mockMvc.perform(delete("/api/admin/posts/" + publishedPost)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("只能归档")));

        // 归档后（曾发布）→ 仍不可删除
        mockMvc.perform(post("/api/admin/posts/" + publishedPost + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/posts/" + publishedPost)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("只能归档")));
    }

    @Test
    void newEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/posts/1/revisions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/posts/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/posts/1/archive")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/posts/1")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private long publishPost(String token, String slug, String title, long categoryId)
            throws Exception {
        long postId = createPost(token);
        saveDraft(postId, token, title, "摘要", "# 正文 " + slug, slug, categoryId, null);
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