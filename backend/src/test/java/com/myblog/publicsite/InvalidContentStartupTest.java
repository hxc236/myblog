package com.myblog.publicsite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 无效必填内容必须使测试与构建失败（#5 测试决策）。
 *
 * <p>对正常内容目录的校验由 {@link PublicSiteApplicationTests} 覆盖（内容一但
 * 损坏，其上下文即无法加载，构建失败）。本测试用一份永久无效的夹具，显式
 * 断言启动会因“公开内容校验失败”而中止，防止校验逻辑被静默移除。
 */
class InvalidContentStartupTest {

    @Test
    void invalidRequiredContentPreventsStartup() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(PublicSiteApplication.class)
                .properties("publicsite.content.location=classpath:publicsite/content-invalid")
                .run())
                .getRootCause()
                .hasMessageContaining("公开内容校验失败");    }
}
