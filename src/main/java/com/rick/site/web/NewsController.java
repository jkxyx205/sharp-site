package com.rick.site.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.news.service.ArticleService.ResolvedArticle;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 新闻前台 Controller(TASK-0704 / TASK-0705 / TASK-1002)。
 *
 * <p>{@code /news} 列表、{@code /news/{slug}} 详情,含 {@code /{locale}/...} 前缀;
 * 语言来自 LocaleContext,i18n 缺失回退租户默认语言;SEO 字段注入 head。
 * SEO meta 由 {@link SeoConfigService} 解析(seo_config 覆盖,缺失回退文章标题/封面/请求 URL)。
 *
 * @author Rick.Xu
 */
@Controller
public class NewsController {

    private final ArticleService articleService;
    private final SeoConfigService seoService;

    public NewsController(ArticleService articleService, SeoConfigService seoService) {
        this.articleService = articleService;
        this.seoService = seoService;
    }

    @GetMapping(value = {"/news", "/{locale:[a-z]{2}-[a-z]{2}}/news",
            "/news/", "/{locale:[a-z]{2}-[a-z]{2}}/news/"})
    public String list(HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(tenant.getDefaultLanguage(), "/news"));
        List<ArticleView> news = articleService.listForDisplay(
                        loc.language(), tenant.getDefaultLanguage()).stream()
                .map(ArticleView::from).toList();
        model.addAttribute("news", news);
        seoService.resolveView(SeoConfigService.NEWS_LIST, null, loc.language(),
                tenant.getDefaultLanguage(),
                new SeoFallback("News", "", "", request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/news";
    }

    @GetMapping(value = {"/news/{slug}", "/{locale:[a-z]{2}-[a-z]{2}}/news/{slug}"})
    public String detail(@org.springframework.web.bind.annotation.PathVariable String slug,
                          HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(tenant.getDefaultLanguage(), "/news/" + slug));
        ResolvedArticle resolved;
        try {
            resolved = articleService.resolveForDisplay(
                    slug, loc.language(), tenant.getDefaultLanguage());
        } catch (BizException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        ArticleView article = ArticleView.from(resolved);
        model.addAttribute("article", article);
        String fbTitle = article.seoTitle() != null ? article.seoTitle() : article.title();
        String fbDesc = article.seoDescription() != null ? article.seoDescription()
                : (article.summary() != null ? article.summary() : "");
        seoService.resolveView(SeoConfigService.ARTICLE, resolved.article().getId(),
                loc.language(), tenant.getDefaultLanguage(),
                new SeoFallback(fbTitle, fbDesc, article.cover(), request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/news-detail";
    }
}
