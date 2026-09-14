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

        // 页面目录:首页/产品列表/新闻列表/关于我们/联系我们/信息
        assertThat(m.pages()).hasSize(6);
        assertThat(m.pages()).map(ThemeManifest.ThemePage::path)
                .contains("/", "/products", "/news", "/about", "/contact", "/info");
        assertThat(m.pages()).map(ThemeManifest.ThemePage::template)
                .contains("themes/modern/index", "themes/modern/about", "themes/modern/contact", "themes/modern/info");
        assertThat(m.pages()).map(ThemeManifest.ThemePage::label).contains("首页", "关于我们", "信息");

        // seo_config:每页声明 en-US/zh-CN 默认;ar-SA 等未声明语种回退默认 en-US
        ThemeManifest.ThemePage home = m.pages().stream()
                .filter(p -> "/".equals(p.path())).findFirst().orElseThrow();
        assertThat(home.seoConfig()).containsKeys("en-US", "zh-CN");
        assertThat(home.seoFor("zh-CN", "en-US")).hasValueSatisfying(seo ->
                assertThat(seo.title()).isEqualTo("欢迎来到我们的网站"));
        // 未声明语种回退默认语种 en-US
        assertThat(home.seoFor("ar-SA", "en-US")).hasValueSatisfying(seo ->
                assertThat(seo.title()).isEqualTo("Welcome to Our Site"));
        // 无 seo_config 的页仍安全返回 empty(theme.json 当前各页均有,此为契约兜底)
        assertThat(new ThemeManifest.ThemePage("/x", "t", "l", null).seoFor("en-US", "en-US"))
                .isEmpty();
    }
}
