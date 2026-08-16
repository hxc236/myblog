package com.myblog.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #15 数据库读路径：真实 PostgreSQL（Testcontainers）上的 Flyway、约束、
 * 排序行为与正式公开 API 契约。
 *
 * <p>测试接缝是“HTTP/API → Spring Boot 应用服务 → 真实 PostgreSQL”
 * （#14 测试决策）：不断言控制器或内部实现细节。
 */
@Testcontainers
@SpringBootTest(
        classes = BackendApplication.class,
        properties = {"site.origin=http://localhost:8080"})
@AutoConfigureMockMvc
class SiteApiDatabaseTest {

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
    private JdbcTemplate jdbcTemplate;

    // ---- 空 PostgreSQL 由 Flyway 建立结构并写入初始值 ----

    @Test
    void emptyPostgresIsMigratedByFlywayWithBaselineSeed() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public_introduction", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM public_introduction WHERE id = 1", String.class))
                .isEqualTo("hxc236");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM skill_groups", Integer.class))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM skill_group_items", Integer.class))
                .isEqualTo(20);
        // 七个迁移（#15–#20）都成功执行
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(11);
    }

    @Test
    void publicIntroductionIsASingletonRow() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO public_introduction (id, display_name, headline, introduction)"
                        + " VALUES (2, 'x', 'y', 'z')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 数据库约束行为 ----

    @Test
    void skillGroupNameIsUniqueInDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO skill_groups (name, position) VALUES (?, ?)", "全栈与桌面", 99))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void skillGroupPositionIsUniqueInDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO skill_groups (name, position) VALUES (?, ?)", "临时分组", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void skillGroupItemPositionIsUniqueWithinGroup() {
        Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM skill_groups WHERE name = '全栈与桌面'", Long.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO skill_group_items (group_id, name, position) VALUES (?, ?, ?)",
                groupId, "临时技能", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 排序行为 ----

    @Test
    void skillGroupOrderFollowsPositionColumn() throws Exception {
        jdbcTemplate.update("UPDATE skill_groups SET position = 5 WHERE name = '全栈与桌面'");
        try {
            mockMvc.perform(get("/api/site/introduction"))
                    .andExpect(jsonPath("$.skillGroups[0].name").value("AI 应用"))
                    .andExpect(jsonPath("$.skillGroups[4].name").value("全栈与桌面"));
        } finally {
            jdbcTemplate.update("UPDATE skill_groups SET position = 0 WHERE name = '全栈与桌面'");
        }
    }

    @Test
    void skillGroupItemOrderFollowsPositionColumn() throws Exception {
        Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM skill_groups WHERE name = '全栈与桌面'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO skill_group_items (group_id, name, position) VALUES (?, ?, ?)",
                groupId, "新技能", 99);
        try {
            mockMvc.perform(get("/api/site/introduction"))
                    .andExpect(jsonPath("$.skillGroups[0].skills[5]").value("新技能"));
        } finally {
            jdbcTemplate.update("DELETE FROM skill_group_items WHERE name = '新技能'");
        }
    }

    // ---- 正式公开 API：/api/site/introduction ----

    @Test
    void introductionIsServedFromDatabaseWithSeedValuesAnonymously() throws Exception {
        mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("public"),
                                org.hamcrest.Matchers.containsString("max-age=300"))))
                .andExpect(jsonPath("$.displayName").value("hxc236"))
                .andExpect(jsonPath("$.headline").value("构建可靠的全栈应用"))
                .andExpect(jsonPath("$.introduction").value(
                        "AI时代，技术栈、语言已不是门槛。我是一名正在成长的全栈开发者，"
                                + "正在探索与 AI Agent 结合的应用开发路线，"
                                + "关注从需求分析、产品设计到工程交付的完整过程。"))
                .andExpect(jsonPath("$.skillGroups.length()").value(5))
                .andExpect(jsonPath("$.skillGroups[0].name").value("全栈与桌面"))
                .andExpect(jsonPath("$.skillGroups[0].skills",
                        org.hamcrest.Matchers.contains(
                                "Java", "Spring Boot", "Vue 3", "TypeScript", "Electron")))
                .andExpect(jsonPath("$.skillGroups[1].name").value("AI 应用"))
                .andExpect(jsonPath("$.skillGroups[1].skills",
                        org.hamcrest.Matchers.contains(
                                "AI Agent", "Agent Loop", "Tool Calling", "Pi Agent RPC")))
                .andExpect(jsonPath("$.skillGroups[2].name").value("数据与接口"))
                .andExpect(jsonPath("$.skillGroups[2].skills",
                        org.hamcrest.Matchers.contains(
                                "REST API", "MyBatis-Plus", "MySQL", "SQLite")))
                .andExpect(jsonPath("$.skillGroups[3].name").value("工程交付"))
                .andExpect(jsonPath("$.skillGroups[3].skills",
                        org.hamcrest.Matchers.contains(
                                "Git / GitHub", "Maven", "Docker", "Render")))
                .andExpect(jsonPath("$.skillGroups[4].name").value("软件工程"))
                .andExpect(jsonPath("$.skillGroups[4].skills",
                        org.hamcrest.Matchers.contains(
                                "TDD", "DDD", "Issue-driven Development")));
    }

    @Test
    void introductionContractContainsOnlyAllowedPublicFields() throws Exception {
        String body = mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = new ObjectMapper().readTree(body);
        Set<String> keys = new HashSet<>();
        root.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).as("正式 API 响应字段必须是规格允许的四个公开字段")
                .containsExactlyInAnyOrder("displayName", "headline", "introduction", "skillGroups");

        // 隐私字段禁区（#14 用户故事 4）与 Hero 眉题不得出现；联系方式字段的
        // 缺席由上方严格 key 集合断言保证（"Git / GitHub" 是合法技能名，不能按子串排除）
        String lower = body.toLowerCase();
        for (String forbidden : new String[]{
                "location", "timezone", "availability", "resumeUrl", "resume_url",
                "portrait", "birthday", "phone", "address", "experience", "proficiency",
                "eyebrow"}) {
            assertThat(lower).as("响应不得包含被排除字段 %s", forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    void noVersionedApiPathIsAdded() throws Exception {
        mockMvc.perform(get("/api/v2/introduction"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/site/v1/introduction"))
                .andExpect(status().isNotFound());
    }

    // ---- DATABASE_URL（Neon 形式）经 EnvironmentPostProcessor 映射后启动数据库读路径 ----

    @Test
    void databaseUrlEnvironmentVariableBootsDatabaseReadPath() {
        String url = "postgres://" + POSTGRES.getUsername() + ":" + POSTGRES.getPassword()
                + "@" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
                .properties("DATABASE_URL=" + url)
                .run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM skill_groups", Integer.class)).isEqualTo(5);
            assertThat(context.getBean(
                    com.myblog.backend.service.SiteIntroductionService.class).isAvailable())
                    .isTrue();
        }
    }

    private static org.hamcrest.Matcher<Object> contentTypeCompatibleJson() {
        return null;
    }
}