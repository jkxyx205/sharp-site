package com.rick.site.web;

import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页 Controller(TASK-0502 / TASK-1002)。
 *
 * <p>渲染 modern 首页:hero/company/cta 文案由前端模板 + {@code messages.json} 文案键提供
 * (不再有 {@code home_section} 表);产品/新闻列表由 Phase 6/7 注入。
 * 语言来自 LocaleContext;默认语言 {@code /},其他语言 {@code /{locale}}。
 * SEO meta 由 {@link SeoConfigService} 解析(page_type="/",缺失回退请求 URL)。
 *
 * @author Rick.Xu
 */
@Controller
public class SiteHomeController {

    /** 首页「Latest News」展示条数(取最新已发布文章)。 */
    private static final int HOME_NEWS_LIMIT = 5;
    /** 首页「Featured Products」展示条数(取上架产品前 N)。 */
    private static final int HOME_PRODUCT_LIMIT = 8;

    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;
    private final ProductService productService;
    private final ArticleService articleService;

    public SiteHomeController(SeoConfigService seoService, ThemeManifestResolver manifestResolver,
                             ProductService productService, ArticleService articleService) {
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
        this.productService = productService;
        this.articleService = articleService;
    }

    @GetMapping(value = {"/", "/{locale:[a-z]{2}-[a-z]{2}}", "/{locale:[a-z]{2}-[a-z]{2}}/"})
    public String index(HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String defaultLanguage = manifestResolver.defaultLocale(tenant);
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(defaultLanguage, "/"));

        // 首页精选产品 + 最新文章(与静态发布 StaticSiteGenerator.generateHome 一致)
        model.addAttribute("products", productService.listForDisplay(loc.language(), defaultLanguage).stream()
                .limit(HOME_PRODUCT_LIMIT).map(ProductView::from).toList());
        model.addAttribute("news", articleService.listForDisplay(loc.language(), defaultLanguage).stream()
                .limit(HOME_NEWS_LIMIT).map(ArticleView::from).toList());

        seoService.resolveView("/", null, loc.language(), defaultLanguage,
                new SeoFallback("", "", "", request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/index";
    }
}
