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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #29 全链路验收：登录 → 发布 → Visitor 可见 → Draft 不污染 Published
 * Revision → 搜索 → 媒体 → 统计 → 公开发现（RSS/Sitemap），全部走正式
 * 无版本领域语义 API（真实 PostgreSQL + HTTP 接缝，不触碰 /api/v1）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class Phase2FullChainTest {

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

    @Test
    void fullChainThroughFormalApis() throws Exception {
        // ---- 登录（OAuth 一次性交换码 → 令牌）----
        String token = adminToken();
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("hxc236"));

        // ---- 准备分类与标签 ----
        long categoryId = MAPPER.readTree(
                        mockMvc.perform(post("/api/admin/categories")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"工程实践\"}"))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        long tagId = MAPPER.readTree(
                        mockMvc.perform(post("/api/admin/tags")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Agent\"}"))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // ---- 上传媒体并引用 ----
        byte[] png = imageBytes();
        String mediaUrl = MAPPER.readTree(
                        mockMvc.perform(multipart("/api/admin/media")
                                        .file(new MockMultipartFile("file", "pic.png",
                                                MediaType.IMAGE_PNG_VALUE, png))
                                        .header("Authorization", "Bearer " + token))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("publicUrl").asText();

        // ---- 创建 Draft、保存、发布 ----
        long postId = MAPPER.readTree(
                        mockMvc.perform(post("/api/admin/posts")
                                        .header("Authorization", "Bearer " + token))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"全链路文章\",\"summary\":\"覆盖完整发布流程\","
                                + "\"bodyMarkdown\":\"# 标题\\n\\n![图](" + mediaUrl + ")\","
                                + "\"slug\":\"full-chain\",\"categoryId\":" + categoryId
                                + ",\"tagIds\":[" + tagId + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // ---- Visitor 可见 ----
        mockMvc.perform(get("/api/posts/full-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("全链路文章"))
                .andExpect(jsonPath("$.bodyMarkdown").value(
                        org.hamcrest.Matchers.containsString(mediaUrl)));
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("full-chain"));

        // ---- Draft 修改不污染 Published Revision ----
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"未发布的草稿改动\",\"summary\":\"s\","
                                + "\"bodyMarkdown\":\"# 草稿\",\"slug\":\"full-chain\","
                                + "\"categoryId\":" + categoryId + ",\"tagIds\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/full-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("全链路文章"));

        // ---- 搜索 ----
        mockMvc.perform(get("/api/posts").param("q", "全链路"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("full-chain"));

        // ---- Page View 与统计 ----
        mockMvc.perform(post("/api/posts/full-chain/view")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteTotal").value(1))
                .andExpect(jsonPath("$.topPosts[0].slug").value("full-chain"));

        // ---- 公开发现：RSS 与 Sitemap ----
        String rss = mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rss).contains("full-chain", "全链路文章").doesNotContain("未发布的草稿改动");
        String sitemap = mockMvc.perform(get("/api/site/sitemap.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(sitemap).contains("/blog/full-chain");

        // ---- 全程未使用 /api/v1；/api/v2 不存在 ----
        mockMvc.perform(get("/api/v2/posts")).andExpect(status().isNotFound());
    }

    private byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
