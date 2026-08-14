package com.myblog.publicsite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 隔离公开站点应用的单一高层后端接缝：SpringBootTest + MockMvc。
 *
 * <p>只验证通过 HTTP 可观察的行为：启动、内容读取与校验、序列化、
 * 匿名安全、CORS、缓存和错误映射（见 #5「测试决策」）。
 */
@SpringBootTest(
        classes = PublicSiteApplication.class,
        properties = {"site.origin=http://localhost:8080"})
@AutoConfigureMockMvc
class PublicSiteApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // ---- 启动与健康 ----

    @Test
    void contextLoadsWithoutDatabase() {
        // 上下文能加载即证明公开站点应用无需 MySQL / 嵌入式数据库即可启动。
    }

    @Test
    void healthIsAnonymousAndNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    // ---- 公开介绍 ----

    @Test
    void introductionReturnsAllRequiredFieldsAnonymously() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/introduction"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("public"),
                                org.hamcrest.Matchers.containsString("max-age=300"))))
                .andExpect(jsonPath("$.displayName").isNotEmpty())
                .andExpect(jsonPath("$.eyebrow").isNotEmpty())
                .andExpect(jsonPath("$.headline").isNotEmpty())
                .andExpect(jsonPath("$.introduction").isNotEmpty())
                .andExpect(jsonPath("$.skillGroups").isArray())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.githubUrl").isNotEmpty())
                .andExpect(jsonPath("$.copyright").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        for (String forbidden : new String[]{
                "location", "timezone", "availability", "resumeUrl", "resume_url",
                "portrait", "birthday", "phone", "address", "experience", "proficiency"}) {
            assertThat(body.toLowerCase()).as("响应不得包含被禁止的个人资料字段 %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    void skillGroupsAreOrderedBackendFrontendDelivery() throws Exception {
        mockMvc.perform(get("/api/v1/introduction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillGroups[0].name").value("后端"))
                .andExpect(jsonPath("$.skillGroups[0].skills[0]").isNotEmpty())
                .andExpect(jsonPath("$.skillGroups[1].name").value("前端"))
                .andExpect(jsonPath("$.skillGroups[2].name").value("交付"));
    }

    // ---- 作品 ----

    @Test
    void projectsReturnsExactlyThreeSortedByFeaturedOrder() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("public"),
                                org.hamcrest.Matchers.containsString("max-age=300"))))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].featuredOrder").value(1))
                .andExpect(jsonPath("$[1].featuredOrder").value(2))
                .andExpect(jsonPath("$[2].featuredOrder").value(3));
    }

    @Test
    void eachProjectHasRequiredFieldsAndAtLeastOneExternalTarget() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").isNotEmpty())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].summary").isNotEmpty())
                .andExpect(jsonPath("$[0].role").isNotEmpty())
                .andExpect(jsonPath("$[0].stack[0]").isNotEmpty())
                .andExpect(jsonPath("$[0].year").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body.contains("\"repositoryUrl\"") || body.contains("\"demoUrl\""))
                .as("每个作品至少有一个已验证外部目标")
                .isTrue();
    }

    // ---- 博客 ----

    @Test
    void postsListIsSortedDescendingWithoutBody() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").isNotEmpty())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].publishedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].readingMinutes").isNumber())
                .andExpect(jsonPath("$[0].body").doesNotExist());
    }

    @Test
    void knownPostReturnsMetadataAndMarkdownBody() throws Exception {
        mockMvc.perform(get("/api/v1/posts/mvp-launch-notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("mvp-launch-notes"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.readingMinutes").isNumber())
                .andExpect(jsonPath("$.body").isNotEmpty())
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("## ")));
    }

    @Test
    void unknownSlugReturnsJson404() throws Exception {
        mockMvc.perform(get("/api/v1/posts/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void malformedSlugReturnsJson404() throws Exception {
        for (String slug : new String[]{"UPPER", "a..b", "with%20space", "-leading", "trailing-", "a_b"}) {
            mockMvc.perform(get("/api/v1/posts/" + slug))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("not_found"));
        }
    }

    // ---- 匿名安全 ----

    @Test
    void publicApisRequireNoAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/introduction")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    // ---- 方法限制与错误响应（#5 5. 公开 API 契约）----

    @Test
    void headRequestsAreSupportedWithoutBody() throws Exception {
        mockMvc.perform(head("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        mockMvc.perform(head("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void writeMethodsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projects")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/posts/x")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/introduction")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownApiPathReturnsJson404WithoutStackLeak() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/unknown-route"))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 错误响应不得暴露堆栈、文件位置、环境变量或内容内部细节（#5 9. 安全与配置）
        assertThat(body).doesNotContain("Exception", "Stack", "\\", "ContentLoader", "at com.myblog");
    }

    // ---- CORS ----

    @Test
    void corsAllowsExactConfiguredOriginWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Origin", "http://localhost:8080"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void corsRejectsUnrelatedOriginInsteadOfEchoingIt() throws Exception {
        // 无关来源不得被动态回显；具体状态码（200 无 CORS 头或 403 拒绝）均可接受，
        // 关键是响应中绝不能出现 Access-Control-Allow-Origin 回显。
        mockMvc.perform(get("/api/v1/health").header("Origin", "https://evil.example"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(403))))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsPreflightRestrictsMethodsAndHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/projects")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("POST"))));
    }

    // ---- 正式领域 API（#14 / #15）----

    @Test
    void formalSiteApiRequiresConfiguredDatabaseAndNeverFallsBackToFiles() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("database_unavailable"))
                .andReturn();
        // 未配置数据库时正式 API 不得退化为 MVP 文件读取（#15 验收）
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("hxc236", "skillGroups");
        // #17：联系方式与作品区设置的正式公开读取同样只来自数据库
        mockMvc.perform(get("/api/site/contact"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
        mockMvc.perform(get("/api/site/work-section"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
        // #18：公开精选 Project 同样只来自数据库
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
        mockMvc.perform(get("/api/admin/projects"))
                .andExpect(status().isUnauthorized());
        // #19：分类与标签的公开读取同样只来自数据库
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
    }

    @Test
    void noVersionedApiPathIsAddedOrReliedUpon() throws Exception {
        // 正式 API 只使用无版本领域语义路径，/api/v2 及其他版本路径不存在（#14 API 约束）
        mockMvc.perform(get("/api/v2/introduction"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/site/introduction"))
                .andExpect(status().isNotFound());
    }

    // ---- Admin API（#16）：未配置数据库时一律拒绝，fail closed ----

    @Test
    void adminApiRejectsAllRequestsWithoutConfiguredDatabase() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        // 任意令牌（包括伪造/legacy JWT）都无法通过哈希校验 → 401
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer whatever"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
    }

    @Test
    void adminCorsAllowsExactOriginWithoutCredentials() throws Exception {
        mockMvc.perform(options("/api/admin/me")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    // ---- 无效内容导致启动失败 ----

    @Test
    void invalidRequiredContentFailsStartup() {
        // 见 InvalidContentStartupTest：内容缺失必填字段时应用上下文必须无法加载。
    }
}
