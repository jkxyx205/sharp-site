package com.rick.site.preview.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.home.service.HomeSectionService;
import com.rick.site.home.service.HomeSectionService.ResolvedSection;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.news.service.ArticleService.ResolvedArticle;
import com.rick.site.page.service.SitePageService;
import com.rick.site.page.service.SitePageService.ResolvedPage;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.product.service.ProductService.ResolvedProduct;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 后台预览(TASK-1101):动态渲染前台 Theme 模板,读取最新(draft)数据,
 * 不影响 published 静态站点(静态文件由 Phase 12/13 独立生成)。
 *
 * <p>路由 {@code /preview/**} 经 SecurityConfig 要求管理员认证;租户来自认证主体
 * (AdminContextFilter 覆写 TenantContext),与 Host 无关(§3/§4)。
 * 多语言经 {@code ?lang=zh-CN} 查询参数(默认主题默认语种,来自 theme.json),缺失回退默认语种。
 * 预览页 robots 强制 noindex,避免被索引。
 *
 * @author Rick.Xu
 */
@Controller
public class PreviewController {

    private static final String NOINDEX_ROBOTS = "noindex, nofollow";

    private final HomeSectionService homeSectionService;
    private final SitePageService pageService;
    private final ProductService productService;
    private final ArticleService articleService;
    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;

    /** 列表分页大小;与 StaticSiteGenerator 共用同一配置项,保证预览/线上一致。 */
    @org.springframework.beans.factory.annotation.Value("${sharp.site.page-size:12}")
    private int pageSize;

    public PreviewController(HomeSectionService homeSectionService, SitePageService pageService,
                            ProductService productService, ArticleService articleService,
                            SeoConfigService seoService, ThemeManifestResolver manifestResolver) {
        this.homeSectionService = homeSectionService;
        this.pageService = pageService;
        this.productService = productService;
        this.articleService = articleService;
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
    }

    @GetMapping({"/preview", "/preview/"})
    public String home(@RequestParam(name = "lang", required = false) String lang,
                       HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        LocaleContext.set(new LocaleResolution(language, "/"));
        Map<String, ResolvedSection> sections = homeSectionService.resolveForDisplay(language, dl);
        ResolvedSection hero = sections.get("hero");
        ResolvedSection company = sections.get("company");
        ResolvedSection cta = sections.get("cta");

        model.addAttribute("tagline", text(hero, PreviewController::i18nTitle));
        model.addAttribute("intro", text(hero, PreviewController::i18nSubtitle));
        model.addAttribute("aboutTeaser", text(company, PreviewController::i18nContent));
        model.addAttribute("ctaTitle", text(cta, PreviewController::i18nTitle));
        model.addAttribute("products", List.of());
        model.addAttribute("news", List.of());

        seoService.resolveView(SeoConfigService.HOME, null, language, dl,
                new SeoFallback(text(hero, PreviewController::i18nTitle),
                        text(company, PreviewController::i18nSubtitle),
                        "", request.getRequestURL().toString())).applyTo(model);
        return finish("themes/modern/index", language, model);
    }

    @GetMapping("/preview/products")
    public String productList(@RequestParam(name = "lang", required = false) String lang,
                              @RequestParam(name = "page", defaultValue = "1") int page,
                              HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        LocaleContext.set(new LocaleResolution(language, "/products/page/" + Math.max(1, page) + "/"));
        List<ProductView> products = productService.listForDisplay(language, dl)
                .stream().map(ProductView::from).toList();
        applyPreviewPagination(model, products, page, "/preview/products");
        seoService.resolveView(SeoConfigService.PRODUCTS_LIST, null, language, dl,
                new SeoFallback("Products", "", "", request.getRequestURL().toString())).applyTo(model);
        return finish("themes/modern/products", language, model);
    }

    @GetMapping("/preview/products/{slug}")
    public String productDetail(@PathVariable String slug,
                                @RequestParam(name = "lang", required = false) String lang,
                                HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        LocaleContext.set(new LocaleResolution(language, "/products/" + slug));
        ResolvedProduct resolved;
        try {
            resolved = productService.resolveForDisplay(slug, language, dl);
        } catch (BizException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        ProductView product = ProductView.from(resolved);
        model.addAttribute("product", product);
        String fbTitle = product.seoTitle() != null ? product.seoTitle() : product.name();
        String fbDesc = product.seoDescription() != null ? product.seoDescription()
                : (product.subtitle() != null ? product.subtitle() : "");
        seoService.resolveView(SeoConfigService.PRODUCT, resolved.product().getId(), language, dl,
                new SeoFallback(fbTitle, fbDesc, product.cover(), request.getRequestURL().toString())).applyTo(model);
        return finish("themes/modern/product-detail", language, model);
    }

    @GetMapping("/preview/news")
    public String newsList(@RequestParam(name = "lang", required = false) String lang,
                           @RequestParam(name = "page", defaultValue = "1") int page,
                           HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        LocaleContext.set(new LocaleResolution(language, "/news/page/" + Math.max(1, page) + "/"));
        List<ArticleView> news = articleService.listForDisplay(language, dl)
                .stream().map(ArticleView::from).toList();
        applyPreviewPagination(model, news, page, "/preview/news");
        seoService.resolveView(SeoConfigService.NEWS_LIST, null, language, dl,
                new SeoFallback("News", "", "", request.getRequestURL().toString())).applyTo(model);
        return finish("themes/modern/news", language, model);
    }

    @GetMapping("/preview/news/{slug}")
    public String newsDetail(@PathVariable String slug,
                             @RequestParam(name = "lang", required = false) String lang,
                             HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        LocaleContext.set(new LocaleResolution(language, "/news/" + slug));
        ResolvedArticle resolved;
        try {
            resolved = articleService.resolveForDisplay(slug, language, dl);
        } catch (BizException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        ArticleView article = ArticleView.from(resolved);
        model.addAttribute("article", article);
        String fbTitle = article.seoTitle() != null ? article.seoTitle() : article.title();
        String fbDesc = article.seoDescription() != null ? article.seoDescription()
                : (article.summary() != null ? article.summary() : "");
        seoService.resolveView(SeoConfigService.ARTICLE, resolved.article().getId(), language, dl,
                new SeoFallback(fbTitle, fbDesc, article.cover(), request.getRequestURL().toString())).applyTo(model);
        return finish("themes/modern/news-detail", language, model);
    }

    @GetMapping("/preview/{path}")
    public String page(@PathVariable String path,
                       @RequestParam(name = "lang", required = false) String lang,
                       HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String language = language(tenant, lang);
        String dl = defaultLocale(tenant);
        String pagePath = "/" + path;
        LocaleContext.set(new LocaleResolution(language, pagePath));
        ResolvedPage resolved;
        try {
            resolved = pageService.resolveForDisplay(pagePath, language, dl);
        } catch (BizException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
        model.addAttribute("page", resolved.page());
        model.addAttribute("content", resolved.i18n() != null ? resolved.i18n().getContent() : "");
        String fbTitle = resolved.i18n() != null && resolved.i18n().getTitle() != null
                ? resolved.i18n().getTitle() : resolved.page().getPageKey();
        String fbImage = resolved.i18n() != null ? resolved.i18n().getCover() : "";
        seoService.resolveView(SeoConfigService.PAGE, resolved.page().getId(), language, dl,
                new SeoFallback(fbTitle, "", fbImage, request.getRequestURL().toString())).applyTo(model);
        return finish(resolved.page().getTemplate(), language, model);
    }

    /** 解析预览语言:空则取主题默认语种(theme.json.defaultLocale)。 */
    private String language(Tenant tenant, String lang) {
        return (lang == null || lang.isBlank()) ? defaultLocale(tenant) : lang;
    }

    /** 主题默认语种(取代 tenant.defaultLanguage)。 */
    private String defaultLocale(Tenant tenant) {
        return manifestResolver.defaultLocale(tenant);
    }

    /** 预览统一收尾:覆盖 currentLanguage、localePrefix(预览链接留根,不镜像)、强制 noindex。 */
    private String finish(String view, String language, Model model) {
        model.addAttribute("currentLanguage", language);
        model.addAttribute("localePrefix", "");
        model.addAttribute("robots", NOINDEX_ROBOTS);
        return view;
    }

    /**
     * 预览列表分页:按 pageSize 切片当前页,写入 page/totalPages/prevLink/nextLink。
     * 翻页链接留在预览内({@code {base}?page={n}}),与静态产物的 /page/{n}/ 不同,
     * 由模板按 prevLink/nextLink 原样渲染。
     */
    private <T> void applyPreviewPagination(Model model, List<T> all, int page, String base) {
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int p = Math.min(Math.max(1, page), totalPages);
        int from = Math.min((p - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        model.addAttribute(all.size() == total && all.isEmpty() ? "products" : "listSlice", all.subList(from, to));
        // 上行按集合名绑定:products 列表页用 "products",news 列表页需用 "news"
        // —— 为避免猜测集合名,直接按调用方已 set 的属性覆盖:
        model.addAttribute(paginationSliceKey(base), all.subList(from, to));
        model.addAttribute("page", p);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("prevLink", p > 1 ? base + "?page=" + (p - 1) : null);
        model.addAttribute("nextLink", p < totalPages ? base + "?page=" + (p + 1) : null);
    }

    /** 预览列表分页切片绑定的模型键:products→"products",news→"news"。 */
    private static String paginationSliceKey(String base) {
        return base.endsWith("/products") ? "products" : "news";
    }

    /** 区块 i18n 存在则取其字段,否则空串。 */
    private String text(ResolvedSection section, java.util.function.Function<ResolvedSection, String> getter) {
        return section != null ? getter.apply(section) : "";
    }

    private static String i18nTitle(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getTitle() : "";
    }

    private static String i18nSubtitle(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getSubtitle() : "";
    }

    private static String i18nContent(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getContent() : "";
    }
}
