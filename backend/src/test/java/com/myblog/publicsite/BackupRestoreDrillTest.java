package com.myblog.publicsite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.publicsite.admin.AdminSessionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #28 备份恢复演练（自动化预演，真实 PostgreSQL + HTTP 接缝）。
 *
 * <p>流程：源库写入并提交内容 → pg_dump 逻辑备份（容器内执行）→ 恢复到
 * 全新空库 → Flyway 校验 → 通过正式读路径核对 Published Revision、搜索
 * 投影、站点设置与 Page View 聚合。生产演练由 scripts/restore-drill.sh
 * 执行（gpg 加密 + 离线私钥）；本测试证明演练在空库上可复现且结果可观察。
 * 注意：本测试不启用事务回滚——备份必须看到已提交数据。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
class BackupRestoreDrillTest {

    @Container
    static final PostgreSQLContainer<?> SOURCE_DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mysite")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void sourceDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SOURCE_DB::getJdbcUrl);
        registry.add("spring.datasource.username", SOURCE_DB::getUsername);
        registry.add("spring.datasource.password", SOURCE_DB::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void dumpRestoreIntoFreshDatabaseIsObservableAndVerifiable() throws Exception {
        // ---- 1. 源库写入并提交可核对内容（独立连接/备份必须可见）----
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "drill-post", "备份演练文章", "演练摘要", categoryId);
        mockMvc.perform(post("/api/posts/drill-post/view")).andExpect(status().isOk());

        // ---- 2. 逻辑备份（pg_dump，容器内执行，独立连接读到已提交数据）----
        String dump = SOURCE_DB.execInContainer(
                        "pg_dump", "-U", "test", "--no-owner", "--no-privileges", "mysite")
                .getStdout();
        assertThat(dump).contains("CREATE TABLE", "COPY ");

        // ---- 3. 恢复到全新空库 ----
        try (PostgreSQLContainer<?> restoredDb =
                     new PostgreSQLContainer<>("postgres:16-alpine")
                             .withDatabaseName("mysite")
                             .withUsername("test")
                             .withPassword("test")) {
            restoredDb.start();
            Path dumpFile = Files.createTempFile("myblog-drill", ".sql");
            Files.write(dumpFile, dump.getBytes(StandardCharsets.UTF_8));
            restoredDb.copyFileToContainer(MountableFile.forHostPath(dumpFile), "/tmp/drill.sql");
            org.testcontainers.containers.Container.ExecResult result = restoredDb.execInContainer(
                    "psql", "-U", "test", "-d", "mysite", "-v", "ON_ERROR_STOP=1", "-f", "/tmp/drill.sql");
            assertThat(result.getExitCode())
                    .as("psql 恢复输出：%s", result.getStderr())
                    .isZero();
            Files.deleteIfExists(dumpFile);

            // ---- 4. Flyway 校验（恢复后的结构必须与迁移一致）----
            assertThatCode(() -> Flyway.configure()
                    .dataSource(restoredDb.getJdbcUrl(),
                            restoredDb.getUsername(), restoredDb.getPassword())
                    .load()
                    .validate())
                    .as("恢复后的数据库必须通过 Flyway 校验")
                    .doesNotThrowAnyException();

            // ---- 5. 用恢复库启动应用，走正式读路径核对 ----
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    PublicSiteApplication.class)
                    .properties(
                            "spring.datasource.url=" + restoredDb.getJdbcUrl(),
                            "spring.datasource.username=" + restoredDb.getUsername(),
                            "spring.datasource.password=" + restoredDb.getPassword())
                    .run()) {
                JdbcTemplate restoredJdbc = context.getBean(JdbcTemplate.class);

                // Published Revision 与搜索投影
                assertThat(restoredJdbc.queryForObject(
                        "SELECT count(*) FROM posts WHERE published_revision_id IS NOT NULL",
                        Integer.class)).isEqualTo(1);
                assertThat(restoredJdbc.queryForObject(
                        "SELECT title FROM post_search_documents"
                                + " WHERE post_id = (SELECT id FROM posts WHERE slug = 'drill-post')",
                        String.class)).isEqualTo("备份演练文章");

                // 站点设置（介绍 + 联系方式）
                assertThat(restoredJdbc.queryForObject(
                        "SELECT headline FROM public_introduction WHERE id = 1",
                        String.class)).isEqualTo("构建可靠的全栈应用");
                assertThat(restoredJdbc.queryForObject(
                        "SELECT email FROM contact_settings WHERE id = 1",
                        String.class)).isEqualTo("houxc2249@gmail.com");

                // Page View 聚合
                assertThat(restoredJdbc.queryForObject(
                        "SELECT total FROM page_view_totals"
                                + " WHERE post_id = (SELECT id FROM posts WHERE slug = 'drill-post')",
                        Long.class)).isEqualTo(1L);

                // 正式读服务（公开 API 同路径）：详情与搜索
                assertThat(context.getBean(com.myblog.publicsite.posts.PublicPostService.class)
                        .getPublishedBySlug("drill-post").title)
                        .isEqualTo("备份演练文章");
                assertThat(context.getBean(com.myblog.publicsite.posts.PublicPostService.class)
                        .searchPublished("演练", 1, 10, null, null).total)
                        .isEqualTo(1);
            }
        }
    }

    // ---- 辅助 ----

    private long publishPost(String token, String slug, String title, String summary,
                             long categoryId) throws Exception {
        long postId = createPost(token);
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"summary\":\"" + summary + "\","
                                + "\"bodyMarkdown\":\"# 正文\",\"slug\":\"" + slug + "\","
                                + "\"categoryId\":" + categoryId + ",\"tagIds\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return postId;
    }

    private long createPost(String token) throws Exception {
        return MAPPER.readTree(
                mockMvc.perform(post("/api/admin/posts")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
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
