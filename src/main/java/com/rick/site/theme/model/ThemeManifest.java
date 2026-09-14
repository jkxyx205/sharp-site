package com.rick.site.theme.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * <p>每页可声明 {@code seo_config}(按语种):后台 seo_config 未维护时的默认值,
 * 后台可覆盖(见 {@link com.rick.site.seo.service.SeoConfigService#resolveView})。
 * 仅站点页(路径型)有效;产品/新闻详情在清单中无条目,不受影响。
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

    /**
     * 主题站点页:path(如 /about)、template(如 themes/modern/about)、展示名,
     * 以及可选的 {@code seo_config}(按语种的 SEO 默认值,后台可覆盖)。
     */
    public record ThemePage(String path, String template, String label,
                            @JsonProperty("seo_config") Map<String, ThemePageSeo> seoConfig) {
        /** 紧凑构造:缺失 seo_config 归一化为空 Map,避免 NPE。 */
        public ThemePage {
            if (seoConfig == null) {
                seoConfig = Map.of();
            }
        }

        /**
         * 取该页 SEO 默认值:先当前语种,缺失回退默认语种,再缺失返回 empty。
         * 供 {@link com.rick.site.seo.service.SeoConfigService} 作为 DB 缺失时的回退。
         */
        public Optional<ThemePageSeo> seoFor(String language, String defaultLanguage) {
            if (seoConfig.isEmpty()) {
                return Optional.empty();
            }
            ThemePageSeo hit = seoConfig.get(language);
            if (hit == null && defaultLanguage != null) {
                hit = seoConfig.get(defaultLanguage);
            }
            return Optional.ofNullable(hit);
        }
    }

    /**
     * theme.json 中每页每语种的 SEO 默认值,字段与 {@code SeoConfig} 的 SEO 内容字段一一对应:
     * title/description/keywords/canonical/robots/ogTitle/ogDescription/ogImage。
     * 任一字段留空即不覆盖(继续回退到下一层),由后台 seo_config 或内容回退兜底。
     */
    public record ThemePageSeo(String title, String description, String keywords,
                               String canonical, String robots,
                               String ogTitle, String ogDescription, String ogImage) {
    }
}
