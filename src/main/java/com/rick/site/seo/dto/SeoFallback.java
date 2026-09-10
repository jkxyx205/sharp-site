package com.rick.site.seo.dto;

/**
 * SEO 回退值(TASK-1002):当 seo_config 无对应行时,由内容/请求派生的默认 meta 值。
 *
 * <p>由各 Controller 提供(页面标题、摘要、封面图、当前请求 URL 作 canonical),
 * 经 {@link com.rick.site.seo.service.SeoConfigService#resolveView} 与 seo_config 合并。
 *
 * @author Rick.Xu
 */
public record SeoFallback(String title, String description, String image, String canonical) {
}
