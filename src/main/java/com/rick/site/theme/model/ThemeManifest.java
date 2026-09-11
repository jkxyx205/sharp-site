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
 * @author Rick.Xu
 */
public record ThemeManifest(String defaultLocale, List<String> locales) {

    /** 多语言模式:支持语种多于 1。 */
    public boolean isMultiLanguage() {
        return locales != null && locales.size() > 1;
    }
}
