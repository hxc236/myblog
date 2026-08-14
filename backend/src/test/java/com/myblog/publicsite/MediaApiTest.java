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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #24 Media Asset：统一存储、类型/大小/尺寸校验、引用保护、不可变 URL
 * 与 Markdown 安全（真实 PostgreSQL + HTTP 接缝，本地存储实现）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class MediaApiTest {

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

    // ---- 上传与公开读取 ----

    @Test
    void jpegAndPngUploadServedWithImmutableCache() throws Exception {
        String token = adminToken();
        byte[] jpeg = imageBytes("jpg", 100, 80);

        MvcResult created = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "photo.jpg",
                                MediaType.IMAGE_JPEG_VALUE, jpeg))
                        .param("altText", "示例图片")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(100))
                .andExpect(jsonPath("$.height").value(80))
                .andExpect(jsonPath("$.sizeBytes").isNumber())
                .andExpect(jsonPath("$.checksumSha256").isNotEmpty())
                .andExpect(jsonPath("$.publicUrl").value(
                        org.hamcrest.Matchers.startsWith("/api/media/")))
                .andReturn();
        JsonNode asset = MAPPER.readTree(created.getResponse().getContentAsString());
        String key = asset.get("objectKey").asText();

        mockMvc.perform(get(asset.get("publicUrl").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("immutable"),
                                org.hamcrest.Matchers.containsString("max-age=31536000"))))
                .andExpect(content().bytes(jpeg));

        // 不可变：同一内容重复上传得到同一 object key（内容哈希）
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "photo-copy.jpg",
                                MediaType.IMAGE_JPEG_VALUE, jpeg))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").value(key));
    }

    @Test
    void webpWithVp8xHeaderIsAccepted() throws Exception {
        String token = adminToken();
        byte[] webp = webpVp8x(800, 600);
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "pic.webp",
                                "image/webp", webp))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/webp"))
                .andExpect(jsonPath("$.width").value(800))
                .andExpect(jsonPath("$.height").value(600));
    }

    // ---- 拒绝规则 ----

    @Test
    void gifSvgAndUnknownContentAreRejected() throws Exception {
        String token = adminToken();
        byte[] gif = imageBytes("gif", 10, 10);
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "a.gif",
                                MediaType.IMAGE_GIF_VALUE, gif))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("只接受")));

        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'></svg>"
                .getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "a.svg", "image/svg+xml", svg))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "a.bin",
                                "application/octet-stream", new byte[]{1, 2, 3, 4}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedAndOverDimensionedImagesAreRejected() throws Exception {
        String token = adminToken();
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "big.jpg",
                                MediaType.IMAGE_JPEG_VALUE, new byte[5 * 1024 * 1024 + 1]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("5 MB")));

        byte[] huge = imageBytes("jpg", 4097, 10);
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "huge.jpg",
                                MediaType.IMAGE_JPEG_VALUE, huge))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("4096")));
    }

    // ---- 引用保护 ----

    @Test
    void referencedAssetCannotBeDeletedAndIsMarkedInList() throws Exception {
        String token = adminToken();
        String publicUrl = uploadJpeg(token, 40, 30);
        long categoryId = ensureCategory("工程实践");
        long postId = createPost(token);
        // 草稿正文引用该图片
        saveDraft(postId, token, "带图文章", "s", "# 标题" + "\\n\\n" + "![图](" + publicUrl + ")", "with-image",
                categoryId);

        mockMvc.perform(get("/api/admin/media").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenced").value(true));

        long assetId = jdbcTemplate.queryForObject(
                "SELECT id FROM media_assets ORDER BY id DESC LIMIT 1", Long.class);
        mockMvc.perform(delete("/api/admin/media/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("正被文章引用")));

        // 移除引用后可以删除
        saveDraft(postId, token, "带图文章", "s", "# 标题（已移除图片）", "with-image",
                categoryId);
        mockMvc.perform(delete("/api/admin/media/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(publicUrl))
                .andExpect(status().isNotFound());
    }

    @Test
    void unreferencedAssetCanBeDeletedAndListMarksIt() throws Exception {
        String token = adminToken();
        uploadJpeg(token, 20, 20);
        mockMvc.perform(get("/api/admin/media").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenced").value(false));

        long assetId = jdbcTemplate.queryForObject(
                "SELECT id FROM media_assets ORDER BY id DESC LIMIT 1", Long.class);
        mockMvc.perform(delete("/api/admin/media/" + assetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ---- Markdown 安全 ----

    @Test
    void dangerousRawHtmlInMarkdownIsRejectedAtSave() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = createPost(token);
        for (String dangerous : new String[]{
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "[点我](javascript:alert(1))",
                "<iframe src=https://evil.example></iframe>"}) {
            mockMvc.perform(put("/api/admin/posts/" + postId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"t\",\"categoryId\":" + categoryId
                                    + ",\"bodyMarkdown\":\"" + dangerous + "\",\"slug\":\"safe-slug\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("危险 HTML")));
        }
        // 正常 Markdown 不受影响
        mockMvc.perform(put("/api/admin/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"categoryId\":" + categoryId
                                + ",\"bodyMarkdown\":\"## 标题\\n正常 **加粗** 内容\",\"slug\":\"safe-slug\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void mediaEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/media")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "a.jpg",
                                MediaType.IMAGE_JPEG_VALUE, imageBytes("jpg", 5, 5))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/media/1")).andExpect(status().isUnauthorized());
    }

    // ---- 辅助 ----

    private String uploadJpeg(String token, int width, int height) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "img.jpg",
                                MediaType.IMAGE_JPEG_VALUE, imageBytes("jpg", width, height)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .get("publicUrl").asText();
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

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** 最小可解析的 WebP VP8X 头部（尺寸在扩展块中）。 */
    private byte[] webpVp8x(int width, int height) {
        byte[] data = new byte[30];
        byte[] riff = "RIFF".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(riff, 0, data, 0, 4);
        data[4] = 26;
        byte[] webp = "WEBP".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(webp, 0, data, 8, 4);
        byte[] vp8x = "VP8X".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(vp8x, 0, data, 12, 4);
        data[16] = 10; // chunk size
        int w = width - 1;
        int h = height - 1;
        data[24] = (byte) (w & 0xFF);
        data[25] = (byte) ((w >> 8) & 0xFF);
        data[26] = (byte) ((w >> 16) & 0xFF);
        data[27] = (byte) (h & 0xFF);
        data[28] = (byte) ((h >> 8) & 0xFF);
        data[29] = (byte) ((h >> 16) & 0xFF);
        return data;
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
