package com.rick.site.news.service;

import com.rick.common.http.exception.BizException;
import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.i18n.service.I18nService;
import com.rick.site.news.dao.ArticleDAO;
import com.rick.site.news.dao.ArticleI18nDAO;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.page.service.HtmlSanitizer;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * 新闻服务(TASK-0702 / 0703 / 0705)。
 *
 * <p>租户隔离:查询显式带 tenant_id;按 slug 幂等 upsert,按 (article_id, language) 幂等 upsert i18n。
 * 富文本 content 保存前经 {@link HtmlSanitizer} 清洗;展示按当前语言取 i18n,缺失回退租户默认语言。
 * 新闻分类复用 {@code CategoryService}(type=NEWS),不在本服务重复实现。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class ArticleService extends BaseServiceImpl<ArticleDAO, Article, Long> {

    private final ArticleI18nDAO i18nDAO;
    private final HtmlSanitizer sanitizer;
    private final I18nService i18nService;

    public ArticleService(ArticleDAO baseDAO, ArticleI18nDAO i18nDAO,
                          HtmlSanitizer sanitizer, I18nService i18nService) {
        super(baseDAO);
        this.i18nDAO = i18nDAO;
        this.sanitizer = sanitizer;
        this.i18nService = i18nService;
    }

    public List<Article> listByTenant() {
        return baseDAO.select("1=1 ORDER BY sort, publish_time DESC NULLS LAST, id", Map.of());
    }

    public List<Article> listPublished() {
        return baseDAO.select("status = 1 ORDER BY publish_time DESC NULLS LAST, sort, id", Map.of());
    }

    public Optional<Article> findBySlug(String slug) {
        List<Article> found = baseDAO.select("slug = :slug", Map.of("slug", slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public Article saveArticle(Article article) {
        article.setTenantId(TenantContext.requireTenantId());
        findBySlug(article.getSlug()).ifPresent(existing -> article.setId(existing.getId()));
        return baseDAO.insertOrUpdate(article);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleI18n saveI18n(Long articleId, ArticleI18n i18n) {
        requireOwned(articleId);
        i18n.setArticleId(articleId);
        i18n.setContent(sanitizer.clean(i18n.getContent()));
        findByLanguage(articleId, i18n.getLanguage()).ifPresent(existing -> i18n.setId(existing.getId()));
        return i18nDAO.insertOrUpdate(i18n);
    }

    public ResolvedArticle resolveForDisplay(String slug, String language, String defaultLanguage) {
        Article article = findBySlug(slug)
                .orElseThrow(() -> new BizException("新闻不存在: " + slug));
        return resolve(article, language, defaultLanguage);
    }

    public List<ResolvedArticle> listForDisplay(String language, String defaultLanguage) {
        List<ResolvedArticle> out = new ArrayList<>();
        for (Article article : listPublished()) {
            out.add(resolve(article, language, defaultLanguage));
        }
        return out;
    }

    private ResolvedArticle resolve(Article article, String language, String defaultLanguage) {
        Map<String, ArticleI18n> byLanguage = loadI18nMap(article.getId());
        Optional<ArticleI18n> resolved = i18nService.resolve(byLanguage, language, defaultLanguage);
        String effectiveLanguage = resolved.map(ArticleI18n::getLanguage).orElse(defaultLanguage);
        return new ResolvedArticle(article, resolved.orElse(null), effectiveLanguage);
    }

    Map<String, ArticleI18n> loadI18nMap(Long articleId) {
        Map<String, ArticleI18n> map = new LinkedHashMap<>();
        for (ArticleI18n row : i18nDAO.select("article_id = :articleId", Map.of("articleId", articleId))) {
            map.put(row.getLanguage(), row);
        }
        return map;
    }

    Optional<ArticleI18n> findByLanguage(Long articleId, String language) {
        List<ArticleI18n> found = i18nDAO.select("article_id = :articleId AND language = :language",
                Map.of("articleId", articleId, "language", language));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private Article requireOwned(Long articleId) {
        // selectById 已按 TenantContext 隔离(跨租户查不到),无需再手动比对 tenantId
        return baseDAO.selectById(articleId)
                .orElseThrow(() -> new BizException("新闻不存在: id=" + articleId));
    }

    /** 展示结果:文章 + 命中语言 + i18n(可能为 null)。 */
    public record ResolvedArticle(Article article, ArticleI18n i18n, String language) {
    }
}
