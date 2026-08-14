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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #26 RSS / Sitemap：只含当前公开内容、不输出 Markdown 全文、归档立即
 * 消失、ETag 重新验证（真实 PostgreSQL + HTTP 接缝）。
 */
@Testcontainers
@SpringBootTest(classes = PublicSiteApplication.class)
@AutoConfigureMockMvc
@Transactional
class FeedApiTest {

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

    // ---- RSS ----

    @Test
    void rssContainsOnlyPublishedItemsWithMetadataAndNoMarkdownBody() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "feed-a", "订阅文章A", "摘要A", "# 正文A **加粗**", categoryId);
        publishPost(token, "feed-b", "订阅文章B", "摘要B", "# 正文B", categoryId);
        // 草稿与未发布文章
        long draftId = createPost(token);
        saveDraft(draftId, token, "草稿文章", "s", "# 草稿正文", "feed-draft", categoryId);

        MvcResult result = mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-cache")))
                .andReturn();
        String xml = result.getResponse().getContentAsString();
        Document doc = parse(xml);

        assertThat(nodes(doc, "/rss/channel/item")).hasSize(2);
        assertThat(nodes(doc, "/rss/channel/item/title")).containsExactly("订阅文章B", "订阅文章A");
        assertThat(nodes(doc, "/rss/channel/item/link"))
                .containsExactly("http://example.com/blog/feed-b", "http://example.com/blog/feed-a");
        assertThat(nodes(doc, "/rss/channel/item/guid"))
                .containsExactly("http://example.com/blog/feed-b", "http://example.com/blog/feed-a");
        assertThat(nodes(doc, "/rss/channel/item/pubDate").get(0)).isNotBlank();
        assertThat(nodes(doc, "/rss/channel/item/category").get(0)).isEqualTo("工程实践");
        // 不输出 Markdown 全文
        assertThat(xml).doesNotContain("正文A", "正文B", "草稿正文");
        // 不含草稿 slug
        assertThat(xml).doesNotContain("feed-draft");
    }

    @Test
    void rssLimitsToTwentyItemsAndDropsArchivedImmediately() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long archivedId = 0;
        for (int i = 1; i <= 21; i++) {
            long id = publishPost(token, "many-" + i, "文章" + i, "摘要" + i, "# 正文" + i, categoryId);
            if (i == 1) {
                archivedId = id;
            }
        }
        Document doc = parse(mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(nodes(doc, "/rss/channel/item")).hasSize(20);

        // 归档 → 立即从 RSS 消失
        mockMvc.perform(post("/api/admin/posts/" + archivedId + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        doc = parse(mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(nodes(doc, "/rss/channel/item")).hasSize(20);
        assertThat(mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andReturn().getResponse().getContentAsString())
                .doesNotContain("/blog/many-1<");
    }

    // ---- Sitemap ----

    @Test
    void sitemapContainsOnlyPublicPagesAndPublishedSlugs() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "map-post", "站点地图文章", "s", "# 正文", categoryId);
        long draftId = createPost(token);
        saveDraft(draftId, token, "草稿", "s", "# 正文", "map-draft", categoryId);

        Document doc = parse(mockMvc.perform(get("/api/site/sitemap.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(nodes(doc, "/urlset/url/loc"))
                .contains("http://example.com/", "http://example.com/blog",
                        "http://example.com/blog/map-post");
        assertThat(nodes(doc, "/urlset/url/loc"))
                .doesNotContain("http://example.com/blog/map-draft", "http://example.com/admin",
                        "http://example.com/api/admin");
    }

    @Test
    void sitemapDropsArchivedPostAndRestoresAfterRepublish() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        long postId = publishPost(token, "map-cycle", "循环文章", "s", "# 正文", categoryId);

        mockMvc.perform(post("/api/admin/posts/" + postId + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String afterArchive = mockMvc.perform(get("/api/site/sitemap.xml")
                        .header("Host", "example.com"))
                .andReturn().getResponse().getContentAsString();
        assertThat(afterArchive).doesNotContain("map-cycle");

        // 恢复为 Draft 并重新发布 → 重新可发现
        long revisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM post_revisions WHERE post_id = ? ORDER BY revision_no DESC LIMIT 1",
                Long.class, postId);
        mockMvc.perform(post("/api/admin/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":" + revisionId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/posts/" + postId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String afterRepublish = mockMvc.perform(get("/api/site/sitemap.xml")
                        .header("Host", "example.com"))
                .andReturn().getResponse().getContentAsString();
        assertThat(afterRepublish).contains("/blog/map-cycle");
    }

    // ---- ETag ----

    @Test
    void rssAndSitemapUseEtagRevalidation() throws Exception {
        String token = adminToken();
        long categoryId = ensureCategory("工程实践");
        publishPost(token, "etag-feed", "ETag 文章", "s", "# 正文", categoryId);

        MvcResult first = mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/site/rss.xml")
                        .header("Host", "example.com")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    // ---- 辅助 ----

    private long publishPost(String token, String slug, String title, String summary,
                             String markdown, long categoryId) throws Exception {
        long postId = createPost(token);
        saveDraft(postId, token, title, summary, markdown, slug, categoryId);
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

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private java.util.List<String> nodes(Document doc, String path) throws Exception {
        javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        org.w3c.dom.NodeList list = (org.w3c.dom.NodeList) xpath.evaluate(
                path, doc, javax.xml.xpath.XPathConstants.NODESET);
        java.util.List<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            values.add(list.item(i).getTextContent());
        }
        return values;
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
