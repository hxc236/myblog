package com.myblog.publicsite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.publicsite.admin.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #27 MVP 内容导入与双读比对：正式数据库读路径与旧文件输出在
 * Visitor 可观察语义上等价（真实 PostgreSQL + HTTP 接缝）。
 *
 * <p>master 的公开内容文件与数据库种子同为五组能力（#14 注），导入器与
 * /api/v1 读取同一份真实文件，对照即真实等价验证。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class MvpImportComparisonTest {

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void importRunsOnceAndComparisonProvesEquivalence() throws Exception {
        String token = adminToken();

        // 第一次导入：介绍/作品区/项目/联系方式由 Flyway 种子提供（空库迁移即
        // 完整初始值），导入器负责现有 Blog Post 与搜索投影
        mockMvc.perform(post("/api/admin/import/mvp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introductionImported").value(false))
                .andExpect(jsonPath("$.projectsImported").value(0))
                .andExpect(jsonPath("$.postsImported").value(1));

        // 重复执行：已有数据的领域跳过（空库重复执行结果一致，不建立双向同步）
        mockMvc.perform(post("/api/admin/import/mvp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introductionImported").value(false))
                .andExpect(jsonPath("$.projectsImported").value(0))
                .andExpect(jsonPath("$.postsImported").value(0));

        // ---- 双读比对：Public Introduction + 五组技能 ----
        JsonNode oldIntro = json(getJson("/api/v1/introduction"));
        JsonNode newIntro = json(getJson("/api/site/introduction"));
        assertThat(newIntro.get("displayName").asText()).isEqualTo(oldIntro.get("displayName").asText());
        assertThat(newIntro.get("headline").asText()).isEqualTo(oldIntro.get("headline").asText());
        assertThat(newIntro.get("introduction").asText()).isEqualTo(oldIntro.get("introduction").asText());
        assertThat(newIntro.get("skillGroups")).hasSize(5);
        for (int g = 0; g < 5; g++) {
            assertThat(newIntro.get("skillGroups").get(g).get("name").asText())
                    .isEqualTo(oldIntro.get("skillGroups").get(g).get("name").asText());
            assertThat(newIntro.get("skillGroups").get(g).get("skills"))
                    .isEqualTo(oldIntro.get("skillGroups").get(g).get("skills"));
        }
        // 联系方式与作品区设置（#17 字段来自同一 introduction.json）
        JsonNode contact = json(getJson("/api/site/contact"));
        assertThat(contact.get("email").asText()).isEqualTo(oldIntro.get("email").asText());
        assertThat(contact.get("githubUrl").asText()).isEqualTo(oldIntro.get("githubUrl").asText());
        assertThat(contact.get("copyright").asText()).isEqualTo(oldIntro.get("copyright").asText());
        JsonNode workSection = json(getJson("/api/site/work-section"));
        assertThat(workSection.get("title").asText()).isEqualTo("个人项目展示");

        // ---- 双读比对：最多三个精选 Project（顺序/字段）----
        JsonNode oldProjects = json(getJson("/api/v1/projects"));
        JsonNode newProjects = json(getJson("/api/projects"));
        assertThat(oldProjects).hasSize(3);
        assertThat(newProjects).hasSize(3);
        for (int i = 0; i < 3; i++) {
            JsonNode oldProject = oldProjects.get(i);
            JsonNode newProject = newProjects.get(i);
            assertThat(newProject.get("title").asText()).isEqualTo(oldProject.get("title").asText());
            assertThat(newProject.get("summary").asText()).isEqualTo(oldProject.get("summary").asText());
            assertThat(newProject.get("role").asText()).isEqualTo(oldProject.get("role").asText());
            assertThat(newProject.get("year").asText()).isEqualTo(oldProject.get("year").asText());
            assertThat(newProject.get("stack")).isEqualTo(oldProject.get("stack"));
            assertThat(newProject.get("featuredOrder").asInt())
                    .isEqualTo(oldProject.get("featuredOrder").asInt());
        }

        // ---- 双读比对：Blog Post 内容 / slug / 发布时间 ----
        JsonNode oldPosts = json(getJson("/api/v1/posts"));
        assertThat(oldPosts).hasSize(1);
        String slug = oldPosts.get(0).get("slug").asText();
        JsonNode oldDetail = json(getJson("/api/v1/posts/" + slug));
        JsonNode newDetail = json(getJson("/api/posts/" + slug));
        assertThat(newDetail.get("slug").asText()).isEqualTo(slug);
        assertThat(newDetail.get("title").asText()).isEqualTo(oldDetail.get("title").asText());
        assertThat(newDetail.get("summary").asText()).isEqualTo(oldDetail.get("summary").asText());
        assertThat(newDetail.get("bodyMarkdown").asText())
                .isEqualTo(oldDetail.get("body").asText());
        // 发布时间等价：MVP 是日期（当天 00:00 UTC），正式 API 输出 ISO 时间
        String oldDate = oldDetail.get("publishedAt").asText().substring(0, 10);
        assertThat(newDetail.get("publishedAt").asText()).startsWith(oldDate);
        // 列表可见且按发布时间倒序
        JsonNode newList = json(getJson("/api/posts"));
        assertThat(newList.get("total").asInt()).isEqualTo(1);
        assertThat(newList.get("items").get(0).get("slug").asText()).isEqualTo(slug);

        // ---- /api/v1 不固化为新契约；/api/v2 不存在 ----
        mockMvc.perform(get("/api/v2/introduction")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/posts")).andExpect(status().isNotFound());
    }

    @Test
    void importRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/import/mvp")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private JsonNode json(MvcResult result) throws Exception {
        // 响应体按 UTF-8 解码（避免 MockMvc 默认 ISO-8859-1 中文乱码）
        return MAPPER.readTree(new String(
                result.getResponse().getContentAsByteArray(),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    private MvcResult getJson(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
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
