package com.rick.site.theme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rick.site.theme.model.ThemeManifest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@link ThemeManifestResolver} 能从真实 {@code themes/modern/meta/theme.json}
 * 反序列化页面目录({@link ThemeManifest.ThemePage})。
 */
class ThemeManifestResolverTest {

    @Test
    void loadsModernCatalogsFromThemeJson() {
        ThemeManifestResolver resolver = new ThemeManifestResolver(
                null, new ObjectMapper(), new DefaultResourceLoader());
        ThemeManifest m = resolver.resolveByThemeId("modern");

        // 页面目录:首页/产品列表/新闻列表/关于我们/联系我们
        assertThat(m.pages()).hasSize(5);
        assertThat(m.pages()).map(ThemeManifest.ThemePage::path)
                .contains("/", "/products", "/news", "/about", "/contact");
        assertThat(m.pages()).map(ThemeManifest.ThemePage::template)
                .contains("themes/modern/index", "themes/modern/about", "themes/modern/contact");
        assertThat(m.pages()).map(ThemeManifest.ThemePage::label).contains("首页", "关于我们");
    }
}
