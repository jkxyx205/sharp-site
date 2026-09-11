package com.rick.site.preview.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.news.service.ArticleService.ResolvedArticle;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.product.service.ProductService.ResolvedProduct;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.model.ThemeManifest.ThemePage;
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

/**
 * 后台预览(TASK-1101):动态渲染前台 Theme 模板,读取最新(draft)数据,
 * 不影响 published 静态站点(静态文件由 Phase 12/13 独立生成)。
 *
 * <p>路由 {@code /preview/**} 经 SecurityConfig 要求管理员认证;租户来自认证主体
 * (AdminContextFilter 覆写 TenantContext),与 Host 无关(§3/§4)。
 * 多语言经 {@code ?lang=zh-CN} 查询参数(默认主题默认语种,来自 theme.json),缺失回退默认语种。
 * 预览页 robots 强制 noindex,避免被索引。
 *
 * <p>首页 hero/cta 文案由模板 + {@code messages.json} 文案键提供(无 home_section 表);
 * 静态页(about/contact)由主题清单 {@code pages} 查找渲染(无 site_page 表)。
 *
 * @author Rick.Xu
 */
@Controller
public class PreviewController {

    private static final String NOINDEX_ROBOTS = "noindex, nofollow";

    /** 首页「Latest News」展示条数。 */
    private static final int HOME_NEWS_LIMIT = 5;
    /** 首页「Featured Products」展示条数。 */
    private static final int HOME_PRODUCT_LIMIT = 8;

    private final ProductService productService;
    private final ArticleService articleService;
    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;

    /** 列表分页大小;与 StaticSiteGenerator 共用同一配置项,保证预览/线上一致。 */
    @org.springframework.beans.factory.annotation.Value("${sharp.site.page-size:12}")
    private int pageSize;

    public PreviewController(ProductService productService, ArticleService articleService,
                            SeoConfigService seoService, ThemeManifestResolver manifestResolver) {
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

        model.addAttribute("products", productService.listForDisplay(language, dl).stream()
                .limit(HOME_PRODUCT_LIMIT).map(ProductView::from).toList());
        model.addAttribute("news", articleService.listForDisplay(language, dl).stream()
                .limit(HOME_NEWS_LIMIT).map(ArticleView::from).toList());

        seoService.resolveView("/", null, language, dl,
                new SeoFallback("", "", "", request.getRequestURL().toString())).applyTo(model);
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
        seoService.resolveView("/products", null, language, dl,
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
        seoService.resolveView("/news", null, language, dl,
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
        // 页面改由前端模板维护:按路径在主题清单 pages 中查找,无则 404
        ThemePage page = manifestResolver.resolve(tenant).pages().stream()
                .filter(p -> p.path().equals(pagePath))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "页面不存在: " + pagePath));
        model.addAttribute("page", page);
        // 单页 SEO:page_type = 页面路径(如 /about),page_id 恒为空
        seoService.resolveView(page.path(), null, language, dl,
                new SeoFallback(page.label(), "", "", request.getRequestURL().toString())).applyTo(model);
        return finish(page.template(), language, model);
    }

    /** 解析预览语言:空则取主题默认语种(theme.json.defaultLocale)。 */
    private String language(Tenant tenant, String lang) {
        return (lang == null || lang.isBlank()) ? defaultLocale(tenant) : lang;
    }

    /** 主题默认语种(取代 tenant.defaultLanguage)。 */
    private String defaultLocale(Tenant tenant) {
        return manifestResolver.defaultLocale(tenant);
    }

    /**
     * 预览统一收尾:覆盖 currentLanguage、localePrefix、强制 noindex。
     *
     * <p>localePrefix 置为 {@code /preview}:模板中所有基于 localePrefix 的链接(nav 菜单、
     * 列表/详情、表单 action)均带上 /preview 前缀,使预览内点击导航不跳出预览模式
     * (对应路由 /preview、/preview/{path}、/preview/products、/preview/news 等)。语种切换
     * 仍走 ?lang= 参数(language 切换片段在预览不渲染),无 locale 路径镜像。
     */
    private String finish(String view, String language, Model model) {
        model.addAttribute("currentLanguage", language);
        model.addAttribute("localePrefix", "/preview");
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
}
