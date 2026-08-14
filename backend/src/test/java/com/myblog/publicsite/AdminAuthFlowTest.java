package com.myblog.publicsite;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.myblog.publicsite.admin.AdminOAuthSuccessHandler;
import com.myblog.publicsite.admin.AdminSessionService;
import com.myblog.publicsite.admin.TokenUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #16 登录与会话边界（真实 PostgreSQL + HTTP 接缝）。
 *
 * <p>GitHub 授权码交换本身由 Spring Security OAuth2 Client 处理（无法在
 * MockMvc 中打真实 GitHub），因此 allowlist 决策通过真实处理器 + 真实
 * {@link OAuth2AuthenticationToken} 调用验证；一次性交换码、令牌换取、
 * 会话校验、撤销、过期与 CORS 全部通过 HTTP 验证。
 */
@Testcontainers
@SpringBootTest(
        classes = PublicSiteApplication.class,
        properties = {
                "site.origin=http://localhost:8080",
                "site.admin.github-allowlist=hxc236"})
@AutoConfigureMockMvc
class AdminAuthFlowTest {

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
    private AdminOAuthSuccessHandler oauthSuccessHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- OAuth 回调 → allowlist → 一次性交换码 ----

    @Test
    void allowlistedGithubUserReceivesOneTimeExchangeCodeRedirect() throws Exception {
        OAuth2AuthenticationToken authentication = githubLogin("hxc236");

        MockHttpServletResponse response = handleOAuthSuccess(authentication);

        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("http://localhost:8080/admin/login?code=");
        String code = redirect.substring(redirect.indexOf('=') + 1);

        // 交换码真实可用：换取令牌后能通过 Admin API 校验
        String token = exchangeCode(code);
        assertThat(token).isNotBlank();
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("hxc236"));
    }

    @Test
    void nonAllowlistedGithubUserIsRedirectedWithForbiddenAndGetsNoCode() throws Exception {
        OAuth2AuthenticationToken authentication = githubLogin("mallory");
        int codesBefore = codeCount();

        MockHttpServletResponse response = handleOAuthSuccess(authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:8080/admin/login?error=forbidden");
        assertThat(codeCount()).isEqualTo(codesBefore);
    }

    @Test
    void allowlistComparisonIsCaseInsensitiveForGithubLogins() throws Exception {
        MockHttpServletResponse response = handleOAuthSuccess(githubLogin("HXC236"));
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:8080/admin/login?code=");
    }

    // ---- 一次性交换码 ----

    @Test
    void exchangeCodeIsSingleUse() throws Exception {
        String code = sessionService.createExchangeCode("hxc236");

        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.login").value("hxc236"))
                .andExpect(jsonPath("$.expiresIn").value(8 * 3600));

        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_code"));
    }

    @Test
    void unknownOrBlankExchangeCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"not-a-real-code\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_code"));
        mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_code"));
    }

    // ---- 会话令牌：校验、过期、撤销、legacy JWT ----

    @Test
    void adminApiRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void adminApiRejectsLegacyJwt() throws Exception {
        String legacyJwt = Jwts.builder()
                .setSubject("legacy-user")
                .claim("user_id", 1)
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(
                        "legacy-test-secret-legacy-test-secret-1234".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + legacyJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void expiredSessionTokenIsRejected() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO admin_sessions (token_hash, owner_login, expires_at)"
                        + " VALUES (?, 'hxc236', now() - interval '1 hour')",
                TokenUtil.sha256Hex("expired-token"));
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void logoutRevokesTokenAndSubsequentCallsAreRejected() throws Exception {
        String token = fullFlowToken();

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutValidTokenIsRejectedLikeAnyAdminApi() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void logoutWithRevokedTokenIsRejected() throws Exception {
        String token = fullFlowToken();
        mockMvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---- 令牌/交换码不出现在 URL 或日志 ----

    @Test
    void sessionTokenNeverAppearsInLogs() throws Exception {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
        try {
            String token = fullFlowToken();
            assertThat(token).isNotBlank();
            assertThat(appender.list)
                    .as("日志中不得出现会话令牌")
                    .noneMatch(event -> event.getFormattedMessage().contains(token));
        } finally {
            ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
        }
    }

    // ---- CORS（生产只允许精确前端来源）----

    @Test
    void adminCorsAllowsExactOriginWithAuthorizationHeaderForPreflight() throws Exception {
        mockMvc.perform(options("/api/admin/me")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void adminCorsRejectsUnrelatedOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(403))))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---- 辅助 ----

    private int codeCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_oauth_codes", Integer.class);
    }

    private MockHttpServletResponse handleOAuthSuccess(OAuth2AuthenticationToken authentication)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        oauthSuccessHandler.onAuthenticationSuccess(request, response, authentication);
        return response;
    }

    private OAuth2AuthenticationToken githubLogin(String login) {
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("login", login),
                "login");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "github");
    }

    private String exchangeCode(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
    }

    /** 完整流程：创建交换码 → 换取令牌。 */
    private String fullFlowToken() throws Exception {
        return exchangeCode(sessionService.createExchangeCode("hxc236"));
    }
}
