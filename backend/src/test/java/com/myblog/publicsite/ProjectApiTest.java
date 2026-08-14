package com.myblog.publicsite;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #18 Project 管理：CRUD、排序、精选 0–3 规则、外链校验（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional // 每个测试回滚数据库变更，避免共享状态污染
class ProjectApiTest {

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

    // ---- 基础 CRUD ----

    @Test
    void adminCanListCreateUpdateAndDeleteProjects() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/admin/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("个人主页与博客"))
                .andExpect(jsonPath("$[0].stack[0]").value("Spring Boot"))
                .andExpect(jsonPath("$[0].featuredOrder").value(1));

        // 新增
        String created = mockMvc.perform(post("/api/admin/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新作品\",\"summary\":\"成果说明\",\"role\":\"全栈\","
                                + "\"year\":\"2026\",\"stack\":[\"Go\"],"
                                + "\"repositoryUrl\":\"https://github.com/x/new\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.displayOrder").value(3))
                .andReturn().getResponse().getContentAsString();
        long newId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created).get("id").asLong();

        // 编辑（含精选槽位调整与排序前移）
        mockMvc.perform(put("/api/admin/projects/" + newId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新作品改\",\"summary\":\"成果说明2\",\"role\":\"全栈\","
                                + "\"year\":\"2026\",\"stack\":[\"Go\",\"Rust\"],"
                                + "\"demoUrl\":\"https://demo.example\","
                                + "\"displayOrder\":0,\"featuredOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新作品改"))
                .andExpect(jsonPath("$.stack[1]").value("Rust"))
                .andExpect(jsonPath("$.featuredOrder").value(1));

        // 删除
        mockMvc.perform(delete("/api/admin/projects/" + newId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void updateNotFoundReturns404() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/admin/projects/99999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"summary\":\"y\",\"role\":\"z\",\"year\":\"2026\","
                                + "\"stack\":[\"Go\"],\"repositoryUrl\":\"https://g/x\","
                                + "\"displayOrder\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void projectWithoutExternalTargetIsRejected() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"summary\":\"y\",\"role\":\"z\",\"year\":\"2026\","
                                + "\"stack\":[\"Go\"],\"displayOrder\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ---- 首页精选规则 ----

    @Test
    void publicProjectsReturnOnlyFeaturedInOrder() throws Exception {
        // seed：三个精选（1/2/3）
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("个人主页与博客"))
                .andExpect(jsonPath("$[1].featuredOrder").value(2))
                .andExpect(jsonPath("$[2].featuredOrder").value(3));
    }

    @Test
    void noFeaturedProjectsReturnsEmptyListForHomepage() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 取消全部精选
        jdbcTemplate.update("UPDATE projects SET featured_order = NULL");

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void featuredSlotIsUniqueAndReplacesPreviousOccupant() throws Exception {
        String token = adminToken();
        // 把第二个作品也放到槽位 1：原槽位 1 的作品让位，精选数量仍为 2
        long secondId = projectIdByDisplayOrder(1);
        mockMvc.perform(put("/api/admin/projects/" + secondId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"AI 求职助手\",\"summary\":\"s\",\"role\":\"r\","
                                + "\"year\":\"2026\",\"stack\":[\"Electron\"],"
                                + "\"repositoryUrl\":\"https://github.com/hxc236/jobhunt-ai-helper\","
                                + "\"displayOrder\":1,\"featuredOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredOrder").value(1));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("AI 求职助手"))
                .andExpect(jsonPath("$[1].featuredOrder").value(3));
    }

    @Test
    void databaseRejectsFourthFeaturedSlot() {
        // 部分唯一索引兜底：直接插入第 4 个精选必然违反唯一约束
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO projects (title, summary, role, year, repository_url,"
                        + " display_order, featured_order)"
                        + " VALUES ('x', 'y', 'z', '2026', 'https://g/x', 99, 2)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void featuredOrderOutOfRangeIsRejected() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"summary\":\"y\",\"role\":\"z\",\"year\":\"2026\","
                                + "\"stack\":[\"Go\"],\"repositoryUrl\":\"https://g/x\","
                                + "\"featuredOrder\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ---- 排序 ----

    @Test
    void displayOrderMoveShiftsOtherProjects() throws Exception {
        String token = adminToken();
        long thirdId = projectIdByDisplayOrder(2);
        // 第三个作品移到最前
        mockMvc.perform(put("/api/admin/projects/" + thirdId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Personal Workbench\",\"summary\":\"s\",\"role\":\"r\","
                                + "\"year\":\"2026\",\"stack\":[\"TypeScript\"],"
                                + "\"repositoryUrl\":\"https://github.com/hxc236/personal-workbench\","
                                + "\"displayOrder\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Personal Workbench"))
                .andExpect(jsonPath("$[1].title").value("个人主页与博客"))
                .andExpect(jsonPath("$[2].title").value("AI 求职助手"));
    }

    // ---- 未授权 ----

    @Test
    void adminProjectsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private long projectIdByDisplayOrder(int displayOrder) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE display_order = ?", Long.class, displayOrder);
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
