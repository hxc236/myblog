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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #23 搜索与投影重建（真实 PostgreSQL + HTTP 接缝）：真实中文数据、
 * 排序稳定性、短/长查询路径、可见性边界与查询计划。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class SearchApiTest {

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

    // ---- 中文短查询（1–2 字符，ILIKE）----

    @Test
    void oneAndTwoCharChineseQueriesMatchTitleAndSummary() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "zh-a", "全栈开发实践", "前端与后端的完整链路", categoryId);
        publishPost(token, "zh-b", "数据工程笔记", "全栈数据管道建设", categoryId);

        mockMvc.perform(get("/api/posts").param("q", "全"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].title").value("全栈开发实践"));

        mockMvc.perform(get("/api/posts").param("q", "全栈"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].title").value("全栈开发实践"));
    }

    // ---- 排序：标题匹配 > 摘要匹配 > 发布时间 ----

    @Test
    void rankingPrefersTitleMatchThenSummaryMatchThenPublishTime() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "rank-a", "AI 智能助手实战", "完全无关的摘要", categoryId);
        publishPost(token, "rank-b", "普通文章", "关于 AI 智能的深度内容", categoryId);
        publishPost(token, "rank-c", "另一篇", "也讨论 AI 智能话题", categoryId);

        mockMvc.perform(get("/api/posts").param("q", "AI 智能"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("rank-a"))
                .andExpect(jsonPath("$.items[1].slug").value("rank-c"))
                .andExpect(jsonPath("$.items[2].slug").value("rank-b"));
    }

    // ---- 长查询（3 字符及以上）与查询计划 ----

    @Test
    void threePlusCharQueryUsesTrigramGinIndex() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "trgm-post", "可靠的全栈应用构建", "工程交付实践", categoryId);

        mockMvc.perform(get("/api/posts").param("q", "可靠的全栈"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("trgm-post"));

        // 查询计划：禁用顺序扫描后必须走 pg_trgm GIN 索引
        jdbcTemplate.execute("SET enable_seqscan = off");
        try {
            List<String> plan = jdbcTemplate.queryForList(
                    "EXPLAIN SELECT d.post_id FROM post_search_documents d"
                            + "  JOIN posts p ON p.id = d.post_id"
                            + " WHERE (d.title ILIKE '%可靠的全栈%' OR d.summary ILIKE '%可靠的全栈%')"
                            + "   AND p.published_revision_id IS NOT NULL",
                    String.class);
            assertThat(String.join("\n", plan))
                    .as("查询计划应使用 trigram GIN 索引")
                    .contains("idx_search_documents_trgm");
        } finally {
            jdbcTemplate.execute("SET enable_seqscan = on");
        }
    }

    // ---- 可见性：正文/草稿/归档不参与搜索 ----

    @Test
    void searchNeverReadsBodyDraftOrArchivedContent() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long publishedId = publishPost(token, "visible-post", "公开文章", "公开摘要", categoryId);
        // 草稿含独特正文与标题（绝不进入投影）
        long draftId = createPost(token);
        saveDraft(draftId, token, "草稿绝密标题", "草稿摘要", "正文只属于草稿 XYZ123", "draft-x", categoryId, null);
        // 已发布文章修改出草稿（草稿标题不进入投影）
        saveDraft(publishedId, token, "公开文章-草稿改", "公开摘要", "# 正文", "visible-post", categoryId, null);

        mockMvc.perform(get("/api/posts").param("q", "XYZ123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/posts").param("q", "草稿绝密"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/posts").param("q", "公开文章"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("公开文章"));
    }

    // ---- 搜索 + 过滤组合 ----

    @Test
    void searchCombinesWithCategoryAndTagFilters() throws Exception {
        String token = adminToken();
        long engineering = ensureCategory("工程实践");
        long ai = ensureCategory("AI 应用");
        long tag = ensureTag("Agent");
        publishPost(token, "comb-a", "AI 智能体工程", "s", engineering, tag);
        publishPost(token, "comb-b", "AI 智能体入门", "s", ai, null);

        mockMvc.perform(get("/api/posts")
                        .param("q", "AI 智能体")
                        .param("category", String.valueOf(engineering)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("comb-a"));

        mockMvc.perform(get("/api/posts")
                        .param("q", "AI 智能体")
                        .param("tag", String.valueOf(tag)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("comb-a"));
    }

    // ---- 参数边界 ----

    @Test
    void searchQueryLengthBoundaries() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "len-post", "边界测试", "s", categoryId);

        mockMvc.perform(get("/api/posts").param("q", ""))
                .andExpect(status().isOk()); // 空 q 视为普通列表
        mockMvc.perform(get("/api/posts").param("q", "长".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ---- 投影重建 ----

    @Test
    void rebuildRestoresProjectionFromPublishedRevisionsOnly() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postA = publishPost(token, "rebuild-a", "重建测试A", "摘要A", categoryId);
        long postB = publishPost(token, "rebuild-b", "重建测试B", "摘要B", categoryId);
        mockMvc.perform(post("/api/admin/posts/" + postA + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 破坏投影
        jdbcTemplate.update("DELETE FROM post_search_documents");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_search_documents", Integer.class)).isZero();

        mockMvc.perform(post("/api/admin/search-index/rebuild")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuilt").value(1));

        mockMvc.perform(get("/api/posts").param("q", "重建测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("rebuild-b"));
    }

    @Test
    void rebuildRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/search-index/rebuild"))
                .andExpect(status().isUnauthorized());
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

    private long publishPost(String token, String slug, String title, String summary,
                             long categoryId) throws Exception {
        return publishPost(token, slug, title, summary, categoryId, null);
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
