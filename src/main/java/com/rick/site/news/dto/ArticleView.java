package com.rick.site.news.dto;

import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService.ResolvedArticle;

import java.time.LocalDateTime;

/**
 * 新闻前台视图(合并 Article + 命中 i18n),供 Thymeleaf 单对象属性访问。
 *
 * <p>列表页用 {@code a.slug/title/publishTime};详情页用 {@code article.title/summary/content}。
 * {@code categorySlug} 来自文章所属分类(type=NEWS),用于模板按分类过滤(分类分组板块)。
 *
 * @author Rick.Xu
 */
public record ArticleView(String slug, String cover, String author, LocalDateTime publishTime,
                          String title, String summary, String content,
                          String seoTitle, String seoDescription,
                          String categorySlug, String categoryName) {

    /** 从解析结果构建;i18n 缺失时 title 回退为 slug,其余为 null。 */
    public static ArticleView from(ResolvedArticle resolved) {
        Article article = resolved.article();
        ArticleI18n i18n = resolved.i18n();
        return new ArticleView(
                article.getSlug(),
                article.getCover(),
                article.getAuthor(),
                article.getPublishTime(),
                i18n != null && i18n.getTitle() != null ? i18n.getTitle() : article.getSlug(),
                i18n != null ? i18n.getSummary() : null,
                i18n != null ? i18n.getContent() : null,
                i18n != null ? i18n.getSeoTitle() : null,
                i18n != null ? i18n.getSeoDescription() : null,
                resolved.categorySlug(),
                resolved.categoryName());
    }
}
