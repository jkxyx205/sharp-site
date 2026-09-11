package com.rick.site.i18n.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.model.ThemeManifest;
import com.rick.site.theme.service.ThemeManifestResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0301 验收测试(Phase 18 修订):LocaleResolver 解析 URL 语言与有效路径。
 *
 * <p>默认语种来自主题清单 {@link ThemeManifest}(theme.json),不再来自 Tenant。
 * URL 段 {@code /zh-cn/...} 取 zh-CN 并剥前缀;无租户回退平台默认 en-US。
 */
class LocaleResolverTest {

    /** 默认主题清单:en-US 默认,locales zh-CN/en-US(与 themes/modern/meta/theme.json 一致)。 */
    private final DefaultLocaleResolver resolver = resolver("en-US", "zh-CN", "en-US");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockHttpServletRequest req(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private Tenant tenant() {
        return Tenant.builder().code("acme").name("Acme").themeId("modern").build();
    }

    /** 构造解析器,其主题清单由 stub 提供(defaultLocale/locales 可定制)。 */
    private static DefaultLocaleResolver resolver(String defaultLocale, String... locales) {
        ThemeManifest manifest = new ThemeManifest(defaultLocale, List.of(locales));
        ThemeManifestResolver mm = new ThemeManifestResolver(null, new ObjectMapper(), null) {
            @Override
            public ThemeManifest resolve(Tenant tenant) {
                return manifest;
            }

            @Override
            public ThemeManifest resolveByThemeId(String themeId) {
                return manifest;
            }
        };
        return new DefaultLocaleResolver(mm);
    }

    @Test
    void defaultLanguageNoPrefix() {
        TenantContext.set(tenant());
        LocaleResolution r = resolver.resolve(req("/"));
        assertThat(r.language()).isEqualTo("en-US");
        assertThat(r.effectivePath()).isEqualTo("/");

        LocaleResolution about = resolver.resolve(req("/about"));
        assertThat(about.language()).isEqualTo("en-US");
        assertThat(about.effectivePath()).isEqualTo("/about");
    }

    @Test
    void zhCnPrefixResolvesAndStrips() {
        TenantContext.set(tenant());
        LocaleResolution r = resolver.resolve(req("/zh-cn/about"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/about");
    }

    @Test
    void zhCnRootStripsToSlash() {
        TenantContext.set(tenant());
        LocaleResolution r = resolver.resolve(req("/zh-cn/"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/");

        LocaleResolution noTrailingSlash = resolver.resolve(req("/zh-cn"));
        assertThat(noTrailingSlash.language()).isEqualTo("zh-CN");
        assertThat(noTrailingSlash.effectivePath()).isEqualTo("/");
    }

    @Test
    void prefixCaseInsensitive() {
        TenantContext.set(tenant());
        assertThat(resolver.resolve(req("/ZH-CN/about")).language()).isEqualTo("zh-CN");
    }

    @Test
    void themeDefaultZhCnBarePathResolvesZhCn() {
        // 主题清单默认语种为 zh-CN 时,无前缀路径解析为 zh-CN
        DefaultLocaleResolver zhResolver = resolver("zh-CN", "zh-CN", "en-US");
        TenantContext.set(tenant());
        LocaleResolution r = zhResolver.resolve(req("/about"));
        assertThat(r.language()).isEqualTo("zh-CN");
        assertThat(r.effectivePath()).isEqualTo("/about");
    }

    @Test
    void unknownPrefixFallsBackToDefault() {
        TenantContext.set(tenant());
        // /de-de/ 未在平台支持语言内,首段不匹配 → 当作默认语言路径
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
