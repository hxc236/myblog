package com.myblog.backend;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开站点应用的单一高层后端接缝（#30 起无文件读路径）。
 *
 * <p>PostgreSQL 是唯一运行时内容权威源：未配置数据库时正式 API 返回 503、
 * 管理端 fail closed；MVP 文件读路径（/api/v1）已移除，一律 404；无任何
 * URL 版本路径。
 */
@SpringBootTest(
        classes = BackendApplication.class,
        properties = {"site.origin=http://localhost:8080"})
@AutoConfigureMockMvc
class BackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // ---- 启动与健康 ----

    @Test
    void contextLoadsWithoutDatabase() {
        // 上下文能加载即证明应用不再需要数据库之外的任何内容源（文件路径已移除）。
    }

    @Test
    void healthIsAnonymousAndNotCached() throws Exception {
        mockMvc.perform(get("/api/site/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(head("/api/site/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    // ---- 正式领域 API 只来自数据库（#15/#17/#18/#19/#21）----

    @Test
    void formalSiteApiRequiresConfiguredDatabaseAndNeverFallsBackToFiles() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/site/introduction"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("database_unavailable"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("hxc236", "skillGroups");

        mockMvc.perform(get("/api/site/contact"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/site/work-section"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/posts/some-slug"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/media/2026/01/abc"))
                .andExpect(status().isServiceUnavailable());
    }

    // ---- Admin API 未配置数据库时一律拒绝，fail closed ----

    @Test
    void adminApiRejectsAllRequestsWithoutConfiguredDatabase() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer whatever"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("database_unavailable"));
        mockMvc.perform(get("/api/admin/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/media")).andExpect(status().isUnauthorized());
    }

    // ---- MVP 文件读路径已退出（#30）：/api/v1 一律 404 ----

    @Test
    void mvpFileReadPathIsRemoved() throws Exception {
        for (String path : new String[]{
                "/api/v1/health", "/api/v1/introduction", "/api/v1/projects",
                "/api/v1/posts", "/api/v1/posts/mvp-launch-notes"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/v2/introduction"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/posts"))
                .andExpect(status().isNotFound());
    }

    // ---- 写方法与未知路径 ----

    @Test
    void writeMethodsAreRejected() throws Exception {
        mockMvc.perform(post("/api/site/introduction")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/posts/x")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/projects")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownApiPathReturnsJson404WithoutStackLeak() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/site/unknown-route"))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "Stack", "at com.myblog");
    }

    // ---- CORS ----

    @Test
    void corsAllowsExactConfiguredOriginWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/site/health").header("Origin", "http://localhost:8080"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void corsRejectsUnrelatedOriginInsteadOfEchoingIt() throws Exception {
        mockMvc.perform(get("/api/site/health").header("Origin", "https://evil.example"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(403))))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsPreflightRestrictsPublicMethods() throws Exception {
        mockMvc.perform(options("/api/site/introduction")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("POST"))));
    }

    @Test
    void adminCorsAllowsExactOriginWithAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/admin/me")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}