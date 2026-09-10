package com.rick.site.i18n.model;

/**
 * Locale 解析结果(ARCHITECTURE.md §7 / TASK-0301)。
 *
 * <p>{@link #language} 为规范语言标签(如 {@code "zh-CN"} / {@code "en-US"});
 * {@link #effectivePath} 为去掉语言前缀后的路径,供路由层映射 Controller
 * (默认语言 {@code /about} 与 {@code /zh-cn/about} 的 effectivePath 均为 {@code /about})。
 *
 * @author Rick.Xu
 */
public record LocaleResolution(String language, String effectivePath) {
}
