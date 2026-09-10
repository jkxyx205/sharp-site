package com.rick.site.seo.dto;

import org.springframework.ui.Model;

/**
 * 解析后的 SEO 视图(TASK-1002):融合 seo_config 与内容回退后的最终 meta 字段。
 *
 * <p>{@link #applyTo(Model)} 一次性写入前台 head 片段所需的全部模型属性,
 * 供 Controller 与静态生成器(Phase 12)复用,避免重复样板。
 *
 * @author Rick.Xu
 */
public record SeoView(String title, String description, String keywords, String canonical,
                      String robots, String ogTitle, String ogDescription, String ogImage) {

    /** 写入前台模型:pageTitle/pageDescription/pageKeywords/canonical/robots/ogTitle/ogDescription/ogImage。 */
    public void applyTo(Model model) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("pageDescription", description);
        model.addAttribute("pageKeywords", keywords);
        model.addAttribute("canonical", canonical);
        model.addAttribute("robots", robots);
        model.addAttribute("ogTitle", ogTitle);
        model.addAttribute("ogDescription", ogDescription);
        model.addAttribute("ogImage", ogImage);
    }
}
