package com.rick.site.theme.model;

import java.util.List;

/**
 * 主题清单(REQUIREMENTS.md §26.1),从 {@code themes/{themeId}/meta/theme.json} 加载。
 *
 * <p>是语种与多语言判定的<strong>唯一来源</strong>(取代 {@code tenant.default_language/languages}):
 * <ul>
 *   <li>{@link #locales()} — 主题支持的语种(规范代码,如 {@code zh-CN} / {@code en-US})。</li>
 *   <li>{@link #defaultLocale()} — 默认语种,必须属于 {@link #locales()};用于回退与单语言发布根。</li>
 *   <li>{@link #isMultiLanguage()} — {@code locales.size() > 1}。</li>
 * </ul>
 *
 * <p><b>页面目录</b>:{@link #pages()} 声明主题提供的全部站点页面
 * (首页/产品列表/新闻列表/关于/联系等):路径、渲染模板、展示名。页面与首页区块均由
 * 前端模板维护,后端只渲染;正文文案由模板 + {@code messages.json} 文案键提供,
 * 不再有 {@code site_page} / {@code home_section} 表。sitemap / 静态生成 / 前台路由
 * /预览/后台 SEO 下拉统一从此枚举。页面 SEO 的 {@code page_type} 取其路径
 * (如 {@code /about}),page_id 恒为空,按 (page_type=path, page_id NULL, language) 存于 seo_config。
 *
 * @author Rick.Xu
 */
public record ThemeManifest(String defaultLocale, List<String> locales, List<ThemePage> pages) {

    /** 多语言模式:支持语种多于 1。 */
    public boolean isMultiLanguage() {
        return locales != null && locales.size() > 1;
    }

    /** 紧凑构造:缺失字段归一化为空列表,避免 NPE。 */
    public ThemeManifest {
        if (locales == null) {
            locales = List.of();
        }
        if (pages == null) {
            pages = List.of();
        }
    }

    /** 主题站点页:path(如 /about)、template(如 themes/modern/about)、展示名。 */
    public record ThemePage(String path, String template, String label) {
    }
}
