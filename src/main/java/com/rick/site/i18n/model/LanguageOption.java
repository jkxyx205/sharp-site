package com.rick.site.i18n.model;

/**
 * 前台语言切换链接项(供 {@code language} 模板片段渲染)。
 *
 * <p>{@code code} 为规范语言标签(如 {@code "zh-CN"}),用于与当前语言比较标记 active;
 * {@code label} 为展示文案(如 {@code "中文"});{@code path} 为切换到该语言的 URL
 * (默认语言无前缀 {@code /products},其他语言带前缀 {@code /zh-cn/products})。
 *
 * @author Rick.Xu
 */
public record LanguageOption(String code, String label, String path) {
}
