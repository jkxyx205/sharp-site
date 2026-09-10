package com.rick.site.i18n.service;

import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0301 验收测试:LocaleResolver 解析 URL 语言与有效路径。
 * 默认语言来自 Tenant.defaultLanguage;{@code /zh-cn/...} 取 zh-CN。
 */
class LocaleResolverTest {

    private final DefaultLocaleResolver resolver = new DefaultLocaleResolver();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockHttpServletRequest req(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private Tenant tenant(String defaultLanguage) {
        return Tenant.builder().code("acme").name("Acme").themeId("modern").defaultLanguage(defaultLanguage).build();
    }

    @Test
    void defaultLanguageNoPrefix() {
        TenantContext.set(tenant("en-US"));
        LocaleResolution r = resolver.resolve(req("/"));
        assertThat(r.language()).isEqualTo("en-US");
        assertThat(r.effectivePath()).isEqualTo("/");

        LocaleResolution about = resolver.resolve(req("/about"));
        assertThat(about.language()).isEqualTo("en-US");
        assertThat(about.effectivePath()).isEqualTo("/about");
    }

    @Test
    void zhCnPrefixResolvesAndStrips() {
        TenantContext.set(tenant("en-US"));
        LocaleResolution r = resolver.resolve(req("/zh-cn/about"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/about");
    }

    @Test
    void zhCnRootStripsToSlash() {
        TenantContext.set(tenant("en-US"));
        LocaleResolution r = resolver.resolve(req("/zh-cn/"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/");

        LocaleResolution noTrailingSlash = resolver.resolve(req("/zh-cn"));
        assertThat(noTrailingSlash.language()).isEqualTo("zh-CN");
        assertThat(noTrailingSlash.effectivePath()).isEqualTo("/");
    }

    @Test
    void prefixCaseInsensitive() {
        TenantContext.set(tenant("en-US"));
        assertThat(resolver.resolve(req("/ZH-CN/about")).language()).isEqualTo("zh-CN");
    }

    @Test
    void zhCnTenantDefaultBarePathResolvesZhCn() {
        TenantContext.set(tenant("zh-CN"));
        LocaleResolution r = resolver.resolve(req("/about"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/about");
    }

    @Test
    void unknownPrefixFallsBackToDefault() {
        TenantContext.set(tenant("en-US"));
        // /de-de/ 未在 Phase 2 支持语言内,首段不匹配 → 当作默认语言路径
        LocaleResolution r = resolver.resolve(req("/de-de/about"));
        assertThat(r.language()).isEqualTo("en-US");
        assertThat(r.effectivePath()).isEqualTo("/de-de/about");
    }

    @Test
    void noTenantFallsBackToPlatformDefault() {
        LocaleResolution r = resolver.resolve(req("/about"));
        assertThat(r.language()).isEqualTo("en-US");
    }
}
