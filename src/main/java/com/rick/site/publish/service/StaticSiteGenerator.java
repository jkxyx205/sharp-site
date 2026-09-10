package com.rick.site.publish.service;

import com.rick.site.home.service.HomeSectionService;
import com.rick.site.home.service.HomeSectionService.ResolvedSection;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.news.dto.ArticleView;
import com.rick.site.news.service.ArticleService;
import com.rick.site.news.service.ArticleService.ResolvedArticle;
import com.rick.site.page.service.SitePageService;
import com.rick.site.product.dto.ProductView;
import com.rick.site.product.service.ProductService;
import com.rick.site.product.service.ProductService.ResolvedProduct;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantConfigService;
import com.rick.site.tenant.service.TenantDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 静态站点生成器(TASK-1201..1206,ARCHITECTURE §9)。
 *
 * <p>发布流程将 DB 中 Tenant/Theme/内容/SEO/i18n 经 Thymeleaf {@link TemplateEngine}
 * 离线渲染为静态 HTML,输出到 {@code {wwwRoot}/{tenantId}/releases/{version}/}。
 * 与前台 Controller 共用同一套 themes/modern 模板与视图装配逻辑,确保预览/线上一致。
 *
 * <p>租户隔离:调用方(Publish)已设置 {@link TenantContext};所有查询经 SiteDatabaseConfig
 * 自动按上下文租户过滤,生成器不另行传 tenantId(§4)。多语言:Phase 12 生成默认语言静态站点;
 * {@code /{locale}/} 镜像留待后续阶段(§7 路由约定)。
 *
 * <p>sitemap/robots 的 base URL 取租户主域名(静态生成无请求上下文),默认 https。
 *
 * @author Rick.Xu
 */
@Service
public class StaticSiteGenerator {

    private static final Logger log = LoggerFactory.getLogger(StaticSiteGenerator.class);

    @Value("${sharp.site.www-root:data/www}")
    private String wwwRoot;

    private final TemplateEngine templateEngine;
    private final HomeSectionService homeSectionService;
    private final SitePageService pageService;
    private final ProductService productService;
    private final ArticleService articleService;
    private final SeoConfigService seoService;
    private final TenantConfigService tenantConfigService;
    private final TenantDomainService domainService;

    public StaticSiteGenerator(TemplateEngine templateEngine,
                              HomeSectionService homeSectionService,
                              SitePageService pageService,
                              ProductService productService,
                              ArticleService articleService,
                              SeoConfigService seoService,
                              TenantConfigService tenantConfigService,
                              TenantDomainService domainService) {
        this.templateEngine = templateEngine;
        this.homeSectionService = homeSectionService;
        this.pageService = pageService;
        this.productService = productService;
        this.articleService = articleService;
        this.seoService = seoService;
        this.tenantConfigService = tenantConfigService;
        this.domainService = domainService;
    }

    /**
     * 生成整站静态文件,返回 release 目录。
     *
     * @param version 发布版本号(用于 releases/{version} 目录)
     * @return 已生成的 release 目录绝对路径
     */
    public Path generate(String version) throws IOException {
        Tenant tenant = TenantContext.require();
        Path releaseDir = releaseDir(tenant.getId(), version);
        Files.createDirectories(releaseDir);
        String language = tenant.getDefaultLanguage();
        String baseUrl = baseUrl(tenant);

        generateHome(tenant, language, baseUrl, releaseDir);
        generatePages(tenant, language, baseUrl, releaseDir);
        generateProducts(tenant, language, baseUrl, releaseDir);
        generateNews(tenant, language, baseUrl, releaseDir);
        generateSitemap(tenant, baseUrl, releaseDir);
        generateRobots(tenant, baseUrl, releaseDir);
        return releaseDir;
    }

    /** TASK-1202:首页 → {@code index.html}。 */
    public void generateHome(Tenant tenant, String language, String baseUrl, Path releaseDir) throws IOException {
        LocaleContext.set(new LocaleResolution(language, "/"));
        OfflineWebContext ctx = newContext();
        Map<String, ResolvedSection> sections = homeSectionService.resolveForDisplay(
                language, tenant.getDefaultLanguage());
        ResolvedSection hero = sections.get("hero");
        ResolvedSection company = sections.get("company");
        ResolvedSection cta = sections.get("cta");

        ctx.setVariable("tagline", text(hero, StaticSiteGenerator::i18nTitle));
        ctx.setVariable("intro", text(hero, StaticSiteGenerator::i18nSubtitle));
        ctx.setVariable("aboutTeaser", text(company, StaticSiteGenerator::i18nContent));
        ctx.setVariable("ctaTitle", text(cta, StaticSiteGenerator::i18nTitle));
        ctx.setVariable("products", List.of());
        ctx.setVariable("news", List.of());
        seoService.resolveView(SeoConfigService.HOME, null, language, tenant.getDefaultLanguage(),
                new SeoFallback(text(hero, StaticSiteGenerator::i18nTitle),
                        text(company, StaticSiteGenerator::i18nSubtitle),
                        "", baseUrl + "/")).applyTo(ctxToModel(ctx));
        applyCommon(ctx, tenant, language);
        write(releaseDir, "index.html", templateEngine.process("themes/modern/index", ctx));
    }

    /** TASK-1203:普通页面 → {@code {path}/index.html}(仅 status=1 的页面)。 */
    public void generatePages(Tenant tenant, String language, String baseUrl, Path releaseDir) throws IOException {
        for (SitePageService.ResolvedPage resolved : pageService.listByTenant().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .map(p -> pageService.resolveForDisplay(p.getPath(), language, tenant.getDefaultLanguage()))
                .toList()) {
            String path = resolved.page().getPath(); // 如 /about
            LocaleContext.set(new LocaleResolution(language, path));
            OfflineWebContext ctx = newContext();
            ctx.setVariable("page", resolved.page());
            ctx.setVariable("content", resolved.i18n() != null ? resolved.i18n().getContent() : "");
            String fbTitle = resolved.i18n() != null && resolved.i18n().getTitle() != null
                    ? resolved.i18n().getTitle() : resolved.page().getPageKey();
            String fbImage = resolved.i18n() != null ? resolved.i18n().getCover() : "";
            seoService.resolveView(SeoConfigService.PAGE, resolved.page().getId(), language,
                    tenant.getDefaultLanguage(),
                    new SeoFallback(fbTitle, "", fbImage, baseUrl + path)).applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, language);
            String rel = path.startsWith("/") ? path.substring(1) : path;
            write(releaseDir, rel + "/index.html",
                    templateEngine.process(resolved.page().getTemplate(), ctx));
        }
    }

    /** TASK-1204:产品列表 + 详情。 */
    public void generateProducts(Tenant tenant, String language, String baseUrl, Path releaseDir) throws IOException {
        List<ResolvedProduct> resolved = productService.listForDisplay(language, tenant.getDefaultLanguage());
        // 列表
        LocaleContext.set(new LocaleResolution(language, "/products"));
        OfflineWebContext listCtx = newContext();
        listCtx.setVariable("products", resolved.stream().map(ProductView::from).toList());
        seoService.resolveView(SeoConfigService.PRODUCTS_LIST, null, language,
                tenant.getDefaultLanguage(),
                new SeoFallback("Products", "", "", baseUrl + "/products")).applyTo(ctxToModel(listCtx));
        applyCommon(listCtx, tenant, language);
        write(releaseDir, "products/index.html",
                templateEngine.process("themes/modern/products", listCtx));

        // 详情
        for (ResolvedProduct rp : resolved) {
            String slug = rp.product().getSlug();
            LocaleContext.set(new LocaleResolution(language, "/products/" + slug));
            OfflineWebContext ctx = newContext();
            ProductView product = ProductView.from(rp);
            ctx.setVariable("product", product);
            String fbTitle = product.seoTitle() != null ? product.seoTitle() : product.name();
            String fbDesc = product.seoDescription() != null ? product.seoDescription()
                    : (product.subtitle() != null ? product.subtitle() : "");
            seoService.resolveView(SeoConfigService.PRODUCT, rp.product().getId(), language,
                    tenant.getDefaultLanguage(),
                    new SeoFallback(fbTitle, fbDesc, product.cover(),
                            baseUrl + "/products/" + slug)).applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, language);
            write(releaseDir, "products/" + slug + "/index.html",
                    templateEngine.process("themes/modern/product-detail", ctx));
        }
    }

    /** TASK-1205:新闻列表 + 详情。 */
    public void generateNews(Tenant tenant, String language, String baseUrl, Path releaseDir) throws IOException {
        List<ResolvedArticle> resolved = articleService.listForDisplay(language, tenant.getDefaultLanguage());
        LocaleContext.set(new LocaleResolution(language, "/news"));
        OfflineWebContext listCtx = newContext();
        listCtx.setVariable("news", resolved.stream().map(ArticleView::from).toList());
        seoService.resolveView(SeoConfigService.NEWS_LIST, null, language,
                tenant.getDefaultLanguage(),
                new SeoFallback("News", "", "", baseUrl + "/news")).applyTo(ctxToModel(listCtx));
        applyCommon(listCtx, tenant, language);
        write(releaseDir, "news/index.html",
                templateEngine.process("themes/modern/news", listCtx));

        for (ResolvedArticle ra : resolved) {
            String slug = ra.article().getSlug();
            LocaleContext.set(new LocaleResolution(language, "/news/" + slug));
            OfflineWebContext ctx = newContext();
            ArticleView article = ArticleView.from(ra);
            ctx.setVariable("article", article);
            String fbTitle = article.seoTitle() != null ? article.seoTitle() : article.title();
            String fbDesc = article.seoDescription() != null ? article.seoDescription()
                    : (article.summary() != null ? article.summary() : "");
            seoService.resolveView(SeoConfigService.ARTICLE, ra.article().getId(), language,
                    tenant.getDefaultLanguage(),
                    new SeoFallback(fbTitle, fbDesc, article.cover(),
                            baseUrl + "/news/" + slug)).applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, language);
            write(releaseDir, "news/" + slug + "/index.html",
                    templateEngine.process("themes/modern/news-detail", ctx));
        }
    }

    /** TASK-1206:sitemap.xml。 */
    public void generateSitemap(Tenant tenant, String baseUrl, Path releaseDir) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(sb, baseUrl + "/");
        pageService.listByTenant().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .forEach(p -> appendUrl(sb, baseUrl + p.getPath()));
        productService.listEnabled().forEach(p ->
                appendUrl(sb, baseUrl + "/products/" + esc(p.getSlug())));
        articleService.listPublished().forEach(a ->
                appendUrl(sb, baseUrl + "/news/" + esc(a.getSlug())));
        sb.append("</urlset>");
        write(releaseDir, "sitemap.xml", sb.toString());
    }

    /** TASK-1206:robots.txt。 */
    public void generateRobots(Tenant tenant, String baseUrl, Path releaseDir) throws IOException {
        String body = "User-agent: *\n" +
                "Allow: /\n" +
                "\n" +
                "Sitemap: " + baseUrl + "/sitemap.xml\n";
        write(releaseDir, "robots.txt", body);
    }

    // ---- helpers ----

    private Path releaseDir(Long tenantId, String version) {
        return Paths.get(wwwRoot).resolve(tenantId.toString()).resolve("releases").resolve(version);
    }

    /** 租户 base URL:取主域名,默认 https。 */
    private String baseUrl(Tenant tenant) {
        List<TenantDomain> domains = domainService.listByTenant();
        String host = domains.stream()
                .filter(d -> d.getIsPrimary() != null && d.getIsPrimary() == 1)
                .map(TenantDomain::getDomain)
                .findFirst()
                .or(() -> domains.stream().map(TenantDomain::getDomain).findFirst())
                .orElse(tenant.getCode() + ".invalid");
        return "https://" + host;
    }

    private OfflineWebContext newContext() {
        return new OfflineWebContext();
    }

    /** 注入 siteName / config / currentLanguage(等同 SiteCommonAttributes,离线渲染无 ControllerAdvice)。 */
    private void applyCommon(OfflineWebContext ctx, Tenant tenant, String language) {
        TenantConfig config = tenantConfigService.findByTenant().orElse(null);
        String siteName = (config != null && config.getCompanyName() != null)
                ? config.getCompanyName() : tenant.getName();
        ctx.setVariable("siteName", siteName);
        ctx.setVariable("config", config);
        ctx.setVariable("currentLanguage", language);
    }

    /**
     * 桥接 Thymeleaf {@link Context} 为 Spring {@link org.springframework.ui.Model},
     * 使 {@link com.rick.site.seo.dto.SeoView#applyTo} 的 Model 写入回灌到 Context
     * (TemplateEngine 渲染读 Context 变量)。applyTo 仅调用 addAttribute(String,Object)。
     */
    private org.springframework.ui.Model ctxToModel(OfflineWebContext ctx) {
        return new ContextModel(ctx);
    }

    /** Model 装饰器:addAttribute 写入底层 Context,而非自身 map。 */
    private static final class ContextModel extends org.springframework.ui.ConcurrentModel {
        private final OfflineWebContext context;

        ContextModel(OfflineWebContext context) {
            this.context = context;
        }

        @Override
        public org.springframework.ui.ConcurrentModel addAttribute(String name, Object value) {
            context.setVariable(name, value);
            return this;
        }
    }

    private void write(Path releaseDir, String relativePath, String content) throws IOException {
        Path target = releaseDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        log.debug("static write: {}", target);
    }

    private void appendUrl(StringBuilder sb, String loc) {
        sb.append("  <url><loc>").append(esc(loc)).append("</loc></url>\n");
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String text(ResolvedSection section, Function<ResolvedSection, String> getter) {
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
