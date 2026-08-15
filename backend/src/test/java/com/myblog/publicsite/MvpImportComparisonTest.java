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
 * MVP 快照导入（#27 双读比对在文件路径存在时完成；#30 起文件路径退出）。
 *
 * <p>本测试验证：一次性导入器从 {@code legacy-mvp-snapshot} 只读快照导入后，
 * 正式领域 API 输出与规格/快照期望一致，且 /api/v1 与 /api/v2 均不存在——
 * PostgreSQL 是唯一运行时内容权威源。
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
    void snapshotImportServesFormalApisAndFilePathsAreGone() throws Exception {
        String token = adminToken();

        // 导入器从只读快照导入（介绍/项目/联系方式由迁移种子提供，导入文章）
        mockMvc.perform(post("/api/admin/import/mvp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postsImported").value(1));

        // Public Introduction：五组能力初始值（#14 注）
        JsonNode intro = json(getJson("/api/site/introduction"));
        assertThat(intro.get("displayName").asText()).isEqualTo("hxc236");
        assertThat(intro.get("headline").asText()).isEqualTo("构建可靠的全栈应用");
        assertThat(intro.get("skillGroups")).hasSize(5);
        assertThat(intro.get("skillGroups").get(0).get("name").asText()).isEqualTo("全栈与桌面");

        // 精选 Project 与联系方式 / 作品区设置
        JsonNode projects = json(getJson("/api/projects"));
        assertThat(projects).hasSize(3);
        assertThat(projects.get(0).get("featuredOrder").asInt()).isEqualTo(1);
        JsonNode contact = json(getJson("/api/site/contact"));
        assertThat(contact.get("email").asText()).isEqualTo("houxc2249@gmail.com");

        // Blog Post：slug / 标题 / 摘要 / 正文 / 发布时间
        JsonNode detail = json(getJson("/api/posts/mvp-launch-notes"));
        assertThat(detail.get("title").asText()).isEqualTo("一天上线个人主页 MVP：架构与取舍");
        assertThat(detail.get("bodyMarkdown").asText()).contains("## ");
        JsonNode list = json(getJson("/api/posts"));
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("slug").asText()).isEqualTo("mvp-launch-notes");
        // 搜索投影可读
        JsonNode search = json(getJson("/api/posts?q=" + encode("MVP")));
        assertThat(search.get("total").asInt()).isEqualTo(1);

        // 文件读路径与版本路径均不存在：PostgreSQL 是唯一运行时权威源
        mockMvc.perform(get("/api/v1/introduction")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/mvp-launch-notes")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/posts")).andExpect(status().isNotFound());
    }

    @Test
    void importRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/import/mvp")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private String encode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }

    private JsonNode json(MvcResult result) throws Exception {
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
