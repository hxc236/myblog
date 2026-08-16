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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #25 匿名 Page View：按 (post, day) 聚合、去重由浏览器本地标记、明细保留
 * 二十四个月、分析只含聚合（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@Transactional
class PageViewApiTest {

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

    // ---- 上报与聚合 ----

    @Test
    void viewReportAggregatesByPostAndDayOnly() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "pv-post", "浏览量测试", categoryId);

        mockMvc.perform(post("/api/posts/pv-post/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(post("/api/posts/pv-post/view"))
                .andExpect(status().isOk());

        // 按 (post_id, day) 聚合成一行，累计值增加两次
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count FROM page_view_daily WHERE post_id = ? AND day = ?",
                Integer.class, postId, LocalDate.now()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total FROM page_view_totals WHERE post_id = ?", Long.class, postId))
                .isEqualTo(2L);
        // 不存在逐次事件表（结构上不可能保存 IP/UA/指纹）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN"
                        + " ('page_view_events', 'visitors', 'page_view_sessions')",
                Integer.class)).isZero();
    }

    @Test
    void unpublishedOrUnknownSlugCannotBeReported() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long draftId = createPost(token);
        saveDraft(draftId, token, "草稿", "s", "# 正文", "draft-pv", categoryId);

        mockMvc.perform(post("/api/posts/draft-pv/view"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/does-not-exist/view"))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredDailyDetailsArePurgedWhileTotalsPersist() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "retention-post", "保留期测试", categoryId);
        // 25 个月前的明细 + 累计
        jdbcTemplate.update(
                "INSERT INTO page_view_daily (post_id, day, count) VALUES (?, ?, 5)",
                postId, LocalDate.now().minusMonths(25));
        jdbcTemplate.update(
                "INSERT INTO page_view_totals (post_id, total) VALUES (?, 5)"
                        + " ON CONFLICT (post_id) DO UPDATE SET total = page_view_totals.total + 5",
                postId);

        mockMvc.perform(post("/api/posts/retention-post/view"))
                .andExpect(status().isOk());

        // 上报触发惰性清理：25 个月前的明细被删除，累计值保留并增加
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM page_view_daily WHERE post_id = ? AND day < ?",
                Integer.class, postId, LocalDate.now().minusMonths(24)))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total FROM page_view_totals WHERE post_id = ?", Long.class, postId))
                .isEqualTo(6L);
    }

    // ---- 分析端点 ----

    @Test
    void analyticsShowsSiteTotalTrendAndTopPostsWithoutVisitorIdentity() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long hot = publishPost(token, "hot-post", "热门文章", categoryId);
        long cold = publishPost(token, "cold-post", "冷门文章", categoryId);
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(post("/api/posts/hot-post/view")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/posts/cold-post/view")).andExpect(status().isOk());
        // 一周前与二十天前的历史明细（30 天窗口内）
        jdbcTemplate.update(
                "INSERT INTO page_view_daily (post_id, day, count) VALUES (?, ?, 3)",
                hot, LocalDate.now().minusDays(7));
        jdbcTemplate.update(
                "INSERT INTO page_view_totals (post_id, total) VALUES (?, 3)"
                        + " ON CONFLICT (post_id) DO UPDATE SET total = page_view_totals.total + 3",
                hot);

        MvcResult result = mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteTotal").value(11))
                .andExpect(jsonPath("$.last30Days.length()").value(30))
                .andExpect(jsonPath("$.topPosts.length()").value(2))
                .andExpect(jsonPath("$.topPosts[0].slug").value("hot-post"))
                .andExpect(jsonPath("$.topPosts[0].total").value(10))
                .andReturn();
        // 不含任何访客身份/独立访客字段
        Set<String> keys = new HashSet<>();
        MAPPER.readTree(result.getResponse().getContentAsString()).fieldNames()
                .forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("siteTotal", "last30Days", "topPosts");
        String body = result.getResponse().getContentAsString();
        for (String forbidden : new String[]{"visitor", "unique", "ip", "userAgent", "fingerprint"}) {
            assertThat(body.toLowerCase()).doesNotContain(forbidden);
        }

        // 单篇 30/90 天趋势（含零值补全）
        mockMvc.perform(get("/api/admin/analytics/posts/" + hot)
                        .param("days", "30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30));
        mockMvc.perform(get("/api/admin/analytics/posts/" + hot)
                        .param("days", "90")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(90));
        mockMvc.perform(get("/api/admin/analytics/posts/" + hot)
                        .param("days", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/analytics/posts/1")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private long publishPost(String token, String slug, String title, long categoryId)
            throws Exception {
        long postId = createPost(token);
        saveDraft(postId, token, title, "摘要", "# 正文 " + slug, slug, categoryId);
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
                           String markdown, String slug, Long categoryId) throws Exception {
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"summary\":\"" + summary + "\","
                                + "\"bodyMarkdown\":\"" + markdown + "\",\"slug\":\"" + slug + "\","
                                + "\"categoryId\":" + categoryId + ",\"tagIds\":[]}"))
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