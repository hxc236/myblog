package com.myblog.backend;

import com.myblog.backend.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #17 站点设置：编辑、保存并发布、原子性、隐私边界（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class SiteSettingsApiTest {

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

    private static final String SEED_SETTINGS = "{"
            + "\"introduction\":{"
            + "\"displayName\":\"hxc236\",\"headline\":\"构建可靠的全栈应用\","
            + "\"introduction\":\"AI时代，技术栈、语言已不是门槛。\","
            + "\"skillGroups\":["
            + "{\"name\":\"全栈与桌面\",\"skills\":[\"Java\",\"Spring Boot\"]},"
            + "{\"name\":\"AI 应用\",\"skills\":[\"AI Agent\"]}]},"
            + "\"workSection\":{\"title\":\"个人项目展示\",\"subtitle\":\"\"},"
            + "\"contact\":{\"email\":\"houxc2249@gmail.com\","
            + "\"githubUrl\":\"https://github.com/hxc236\",\"copyright\":\"© 2026 hxc236\"}"
            + "}";

    // ---- 未授权访问 ----

    @Test
    void adminSiteSettingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/site-settings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/site-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEED_SETTINGS))
                .andExpect(status().isUnauthorized());
    }

    // ---- 读取与保存并发布 ----

    @Test
    void adminCanReadSeedSettingsAndPublishEditsAtomically() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction.displayName").value("hxc236"))
                .andExpect(jsonPath("$.introduction.skillGroups[0].name").value("全栈与桌面"))
                .andExpect(jsonPath("$.workSection.title").value("个人项目展示"))
                .andExpect(jsonPath("$.workSection.subtitle").value(""))
                .andExpect(jsonPath("$.contact.email").value("houxc2249@gmail.com"));

        String updated = "{"
                + "\"introduction\":{"
                + "\"displayName\":\"hxc236\",\"headline\":\"新的 Hero 主标题\","
                + "\"introduction\":\"新的个人介绍。\","
                + "\"skillGroups\":["
                + "{\"name\":\"AI 应用\",\"skills\":[\"AI Agent\",\"Tool Calling\"]},"
                + "{\"name\":\"工程交付\",\"skills\":[\"Docker\"]},"
                + "{\"name\":\"新分组\",\"skills\":[\"新技能\"]}]},"
                + "\"workSection\":{\"title\":\"项目作品\",\"subtitle\":\"副标题文本\"},"
                + "\"contact\":{\"email\":\"owner@example.com\","
                + "\"githubUrl\":\"https://github.com/owner\",\"copyright\":\"© 2026 owner\"}"
                + "}";
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction.headline").value("新的 Hero 主标题"));

        // Visitor 看到最近一次完整发布的内容（排序、增删、联系方式、作品区一起生效）
        mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("新的 Hero 主标题"))
                .andExpect(jsonPath("$.introduction").value("新的个人介绍。"))
                .andExpect(jsonPath("$.skillGroups.length()").value(3))
                .andExpect(jsonPath("$.skillGroups[0].name").value("AI 应用"))
                .andExpect(jsonPath("$.skillGroups[0].skills[1]").value("Tool Calling"))
                .andExpect(jsonPath("$.skillGroups[1].name").value("工程交付"))
                .andExpect(jsonPath("$.skillGroups[2].name").value("新分组"));
        mockMvc.perform(get("/api/site/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/owner"))
                .andExpect(jsonPath("$.copyright").value("© 2026 owner"));
        mockMvc.perform(get("/api/site/work-section"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("项目作品"))
                .andExpect(jsonPath("$.subtitle").value("副标题文本"));
    }

    @Test
    void emptySubtitleIsStoredAndServedWithoutPlaceholder() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEED_SETTINGS))
                .andExpect(status().isOk());

        String withEmptySubtitle = "{"
                + "\"introduction\":{\"displayName\":\"hxc236\",\"headline\":\"H\","
                + "\"introduction\":\"I\",\"skillGroups\":[{\"name\":\"G\",\"skills\":[\"S\"]}]},"
                + "\"workSection\":{\"title\":\"作品区\",\"subtitle\":\"\"},"
                + "\"contact\":{\"email\":\"a@b.c\",\"githubUrl\":\"https://g\",\"copyright\":\"c\"}"
                + "}";
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withEmptySubtitle))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site/work-section"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitle").value(""));
    }

    // ---- 原子性：失败时公开内容不变 ----

    @Test
    void invalidSettingsAreRejectedAndPublicContentStaysUnchanged() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEED_SETTINGS))
                .andExpect(status().isOk());

        String invalid = "{"
                + "\"introduction\":{\"displayName\":\"\",\"headline\":\"H\","
                + "\"introduction\":\"I\",\"skillGroups\":[{\"name\":\"G\",\"skills\":[\"S\"]}]},"
                + "\"workSection\":{\"title\":\"作品区\",\"subtitle\":\"\"},"
                + "\"contact\":{\"email\":\"a@b.c\",\"githubUrl\":\"https://g\",\"copyright\":\"c\"}"
                + "}";
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        // 公开内容保持上一次完整发布（seed），未被部分更新污染
        mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("构建可靠的全栈应用"))
                .andExpect(jsonPath("$.skillGroups[0].name").value("全栈与桌面"));
    }

    @Test
    void duplicateSkillGroupNameIsRejected() throws Exception {
        String token = adminToken();
        String duplicate = "{"
                + "\"introduction\":{\"displayName\":\"hxc236\",\"headline\":\"H\","
                + "\"introduction\":\"I\","
                + "\"skillGroups\":[{\"name\":\"同名\",\"skills\":[\"A\"]},{\"name\":\"同名\",\"skills\":[\"B\"]}]},"
                + "\"workSection\":{\"title\":\"作品区\",\"subtitle\":\"\"},"
                + "\"contact\":{\"email\":\"a@b.c\",\"githubUrl\":\"https://g\",\"copyright\":\"c\"}"
                + "}";
        mockMvc.perform(put("/api/admin/site-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ---- 隐私字段边界 ----

    @Test
    void privacyFieldsInPayloadAreRejected() throws Exception {
        String token = adminToken();
        for (String extra : new String[]{
                "\"location\":\"上海\"",
                "\"resumeUrl\":\"https://example.com/resume\"",
                "\"experience\":[{\"year\":2020}]"}) {
            String payload = "{"
                    + "\"introduction\":{\"displayName\":\"hxc236\",\"headline\":\"H\","
                    + "\"introduction\":\"I\",\"skillGroups\":[{\"name\":\"G\",\"skills\":[\"S\"]}],"
                    + extra + "},"
                    + "\"workSection\":{\"title\":\"作品区\",\"subtitle\":\"\"},"
                    + "\"contact\":{\"email\":\"a@b.c\",\"githubUrl\":\"https://g\",\"copyright\":\"c\"}"
                    + "}";
            mockMvc.perform(put("/api/admin/site-settings")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_failed"));
        }
        // 公开内容未被污染
        mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillGroups[0].name").value("全栈与桌面"));
    }

    // ---- 辅助 ----

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