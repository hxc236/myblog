package com.myblog.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 包根完整性构建级检查（#31 测试决策）：生产入口与环境处理器在新包根下
 * 可解析；已删除的 legacy 类与构建依赖缺席。
 *
 * <p>纯类路径检查，不需要 Spring 上下文或数据库：包移动若把生产类移出
 * {@code com.myblog.backend} 或让 legacy 依赖悄悄回到构建，本测试立即失败。
 */
class PackageRootIntegrityTest {

    // ---- 生产入口与 Spring 环境处理器必须在新包根下可解析 ----

    @Test
    void productionEntryPointResolvesUnderUnifiedRoot() throws Exception {
        Class<?> app = Class.forName("com.myblog.backend.BackendApplication");
        // 可执行生产入口：main(String[]) 必须存在
        assertThat(app.getMethod("main", String[].class)).isNotNull();
    }

    @Test
    void environmentPostProcessorResolvesAndIsRegistered() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.myblog.backend.config.DatabaseUrlEnvironmentPostProcessor"))
                .doesNotThrowAnyException();

        boolean registered = false;
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            try (InputStream in = resources.nextElement().openStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (text.contains(
                        "org.springframework.boot.env.EnvironmentPostProcessor=")
                        && text.contains(
                        "com.myblog.backend.config.DatabaseUrlEnvironmentPostProcessor")) {
                    registered = true;
                }
            }
        }
        assertThat(registered)
                .as("spring.factories 必须把环境处理器注册到新包根")
                .isTrue();
    }

    // ---- 生产类按职责目录分布 ----

    @Test
    void productionClassesLiveUnderExpectedResponsibilityPackages() {
        for (String name : new String[]{
                // config：Spring 配置、安全、过滤器、CORS、数据源、环境处理
                "com.myblog.backend.config.AdminSecurityConfig",
                "com.myblog.backend.config.AdminOAuthSuccessHandler",
                "com.myblog.backend.config.AdminTokenAuthenticationFilter",
                "com.myblog.backend.config.PublicCorsConfig",
                "com.myblog.backend.config.SiteDataSourceConfig",
                "com.myblog.backend.config.SiteDataSourceCondition",
                "com.myblog.backend.config.MediaStorageConfig",
                // utils：无状态工具与快照加载助手
                "com.myblog.backend.utils.TokenUtil",
                "com.myblog.backend.utils.MediaContentValidator",
                "com.myblog.backend.utils.ContentLoader",
                "com.myblog.backend.utils.MvpContentImporter",
                // controller：全部 HTTP 控制器与异常响应处理
                "com.myblog.backend.controller.SiteApiController",
                "com.myblog.backend.controller.PublicPostsController",
                "com.myblog.backend.controller.AdminPostController",
                "com.myblog.backend.controller.AdminAuthController",
                "com.myblog.backend.controller.ApiExceptionHandler",
                // service：业务接口
                "com.myblog.backend.service.PostService",
                "com.myblog.backend.service.PublicPostService",
                "com.myblog.backend.service.AdminSessionService",
                "com.myblog.backend.service.MediaStorage",
                // service/impl：Spring 实现与存储适配器
                "com.myblog.backend.service.impl.PostServiceImpl",
                "com.myblog.backend.service.impl.PublicPostServiceImpl",
                "com.myblog.backend.service.impl.LocalMediaStorage",
                "com.myblog.backend.service.impl.S3MediaStorage",
                // mapper：PostgreSQL 数据访问
                "com.myblog.backend.mapper.PostMapper",
                "com.myblog.backend.mapper.AdminSessionMapper",
                "com.myblog.backend.mapper.ProjectMapper",
                // pojo：元数据与传输数据
                "com.myblog.backend.pojo.SiteSettings",
                "com.myblog.backend.pojo.AdminPostDetail",
                "com.myblog.backend.pojo.MediaAsset",
        }) {
            assertThatCode(() -> Class.forName(name))
                    .as("生产类必须位于 com.myblog.backend 职责包：%s", name)
                    .doesNotThrowAnyException();
        }
    }

    // ---- 已删除的 legacy 类必须缺席 ----

    @Test
    void legacyPublicsiteClassesAreAbsent() {
        for (String name : new String[]{
                "com.myblog.publicsite.PublicSiteApplication",
                "com.myblog.publicsite.web.SiteApiController",
                "com.myblog.publicsite.admin.AdminSessionService",
                "com.myblog.publicsite.media.LocalMediaStorage",
                "com.myblog.publicsite.config.DatabaseUrlEnvironmentPostProcessor",
                "com.myblog.publicsite.content.ContentLoader",
        }) {
            assertThatThrownBy(() -> Class.forName(name))
                    .as("旧包根 %s 不得再存在生产类", name)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void legacyAccountPlanTodoClassesAreAbsent() {
        for (String name : new String[]{
                "com.myblog.backend.controller.HelloController",
                "com.myblog.backend.controller.user.account.LoginController",
                "com.myblog.backend.controller.user.account.RegisterController",
                "com.myblog.backend.mapper.UserMapper",
                "com.myblog.backend.mapper.PlanMapper",
                "com.myblog.backend.mapper.TodoMapper",
                "com.myblog.backend.pojo.User",
                "com.myblog.backend.pojo.Plan",
                "com.myblog.backend.pojo.Todo",
                "com.myblog.backend.utils.JwtUtil",
                "com.myblog.backend.utils.UserUtil",
                "com.myblog.backend.config.filter.JwtAuthenticationTokenFilter",
                "com.myblog.backend.service.plansandtodos.plan.PlanInfoService",
                "com.myblog.backend.service.user.account.LoginService",
        }) {
            assertThatThrownBy(() -> Class.forName(name))
                    .as("legacy %s 不得再存在", name)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    // ---- 已删除的构建依赖必须缺席 ----

    @Test
    void legacyBuildDependenciesAreAbsent() {
        for (String name : new String[]{
                "io.jsonwebtoken.Jwts",
                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                "com.mysql.cj.jdbc.Driver",
                "org.mybatis.spring.SqlSessionFactoryBean",
                "lombok.Lombok",
        }) {
            assertThatThrownBy(() -> Class.forName(name))
                    .as("legacy 构建依赖 %s 必须从类路径移除", name)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void mvpSnapshotIsRetainedOnClasspath() {
        assertThat(getClass().getClassLoader().getResource(
                "legacy-mvp-snapshot/content/posts/mvp-launch-notes.md"))
                .as("MVP 导入快照必须保留在类路径")
                .isNotNull();
    }
}
