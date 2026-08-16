package com.myblog.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DatabaseUrlEnvironmentPostProcessor}：Neon 风格 DATABASE_URL 到
 * Spring 数据源属性的映射（#15 生产接线）。
 */
class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void mapsNeonStylePostgresUrlWithUserInfoToJdbcProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DATABASE_URL",
                "postgres://user:p%40ss@ep-quiet-forest-123456.us-east-2.aws.neon.tech/mysite?sslmode=require");

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(
                "jdbc:postgresql://ep-quiet-forest-123456.us-east-2.aws.neon.tech/mysite?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p%40ss");
    }

    @Test
    void mapsPostgresqlSchemeWithoutUserInfo() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DATABASE_URL", "postgresql://db.example.com:5433/site");

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5433/site");
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        assertThat(environment.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void passesThroughJdbcUrlUnchanged() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DATABASE_URL",
                "jdbc:postgresql://host:5432/db?user=u&password=p");

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host:5432/db?user=u&password=p");
    }

    @Test
    void explicitSpringDatasourceUrlWinsOverDatabaseUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://explicit/db");
        environment.setProperty("DATABASE_URL", "postgres://other/db");

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://explicit/db");
    }

    @Test
    void noDatabaseUrlLeavesEnvironmentUntouched() {
        MockEnvironment environment = new MockEnvironment();

        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains("databaseUrlEnvironmentPostProcessor"))
                .isFalse();
    }

    @Test
    void unsupportedSchemeFailsFast() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.map("mysql://user:pass@host/db"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL");
    }

    @Test
    void directMapHandlesUserInfoWithoutPassword() {
        Map<String, Object> mapped = DatabaseUrlEnvironmentPostProcessor.map("postgres://onlyuser@host/db");
        assertThat(mapped.get("spring.datasource.url")).isEqualTo("jdbc:postgresql://host/db");
        assertThat(mapped.get("spring.datasource.username")).isEqualTo("onlyuser");
        assertThat(mapped.get("spring.datasource.password")).isNull();
    }
}