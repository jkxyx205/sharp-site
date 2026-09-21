package com.rick.site.publish.service;

import com.rick.site.catalog.service.CategoryService;
import com.rick.site.i18n.SupportedLanguage;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LanguageOption;
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
import com.rick.site.tenant.dto.TenantConfigView;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantConfigService;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.theme.model.ThemeManifest;
import com.rick.site.theme.model.ThemeManifest.ThemePage;
import com.rick.site.theme.service.ThemeManifestResolver;
import com.rick.site.theme.service.ThemeResolver;
import com.rick.site.video.dto.VideoView;
import com.rick.site.video.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 静态站点生成器(TASK-1201..1206,ARCHITECTURE §9)。
 *
 * <p>发布流程将 DB 中 Tenant/Theme/内容/SEO/i18n 经 Thymeleaf {@link TemplateEngine}
 * 离线渲染为静态 HTML,输出到 {@code {wwwRoot}/{tenantId}/releases/{version}/}。
 * 与前台 Controller 共用同一套 themes/{themeId} 模板与视图装配逻辑,确保预览/线上一致。
 *
 * <p>租户隔离:调用方(Publish)已设置 {@link TenantContext};所有查询经 SiteDatabaseConfig
 * 自动按上下文租户过滤,生成器不另行传 tenantId(§4)。
 *
 * <p><b>多语言(Phase 18 修订)</b>:语种来自主题清单 {@link ThemeManifest}(theme.json)。
 * <ul>
 *   <li>多语言:默认语种渲染在根目录({@code /products}),其余语种镜像在
 *       {@code /{locale-lowercase}/} 下(如 {@code /zh-cn/products});与前台路由
 *       (DefaultLocaleResolver)约定一致——默认语种无前缀,其余带小写前缀。</li>
 *   <li>单语言:仅默认语种,全部渲染在根目录。</li>
 * </ul>
 * 模板链接经模型变量 {@code localePrefix}(""/默认 或 "/{locale}")前缀化,
 * 使镜像页内链接指向同一语种子树。
 *
 * <p><b>逻辑分页</b>:产品/新闻列表按 {@code pageSize} 切片,产物为
 * {@code {prefix}/{list}/page/{n}/index.html}(n=1..N);{@code {list}/index.html}
 * 为重定向到 {@code page/1/} 的 meta-refresh 跳板页。
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

    /** 列表逻辑分页大小;产品/新闻共用。 */
    @Value("${sharp.site.page-size:12}")
    private int pageSize;

    /** 首页「Latest News」展示条数(取最新已发布文章)。 */
    private static final int HOME_NEWS_LIMIT = 5;
    /** 首页「Featured Products」展示条数(取上架产品前 N)。 */
    private static final int HOME_PRODUCT_LIMIT = 8;
    /** 首页「Featured Videos」展示条数(取上架视频前 N)。 */
    private static final int HOME_VIDEO_LIMIT = 8;

    private final TemplateEngine templateEngine;
    private final ProductService productService;
    private final ArticleService articleService;
    private final SeoConfigService seoService;
    private final TenantConfigService tenantConfigService;
    private final TenantDomainService domainService;
    private final ThemeManifestResolver manifestResolver;
    private final ThemeResolver themeResolver;
    private final ResourcePatternResolver resourcePatternResolver;

    private final CategoryService categoryService;
    private final VideoService videoService;

    public StaticSiteGenerator(TemplateEngine templateEngine,
                              ProductService productService,
                              ArticleService articleService,
                              SeoConfigService seoService,
                              TenantConfigService tenantConfigService,
                              TenantDomainService domainService,
                              ThemeManifestResolver manifestResolver,
                              ThemeResolver themeResolver,
                              ResourcePatternResolver resourcePatternResolver,
                              CategoryService categoryService,
                              VideoService videoService) {
        this.templateEngine = templateEngine;
        this.productService = productService;
        this.articleService = articleService;
        this.seoService = seoService;
        this.tenantConfigService = tenantConfigService;
        this.domainService = domainService;
        this.manifestResolver = manifestResolver;
        this.themeResolver = themeResolver;
        this.resourcePatternResolver = resourcePatternResolver;
        this.categoryService = categoryService;
        this.videoService = videoService;
    }

    /**
     * 生成整站静态文件,返回 release 目录。
     *
     * @param version 发布版本号(用于 releases/{version} 目录)
     * @return 已生成的 release 目录绝对路径
     */
    public Path generate(String version) throws IOException {
        Tenant tenant = TenantContext.require();
        Path releaseDir = releaseDir(tenant.getCode(), version);
        Files.createDirectories(releaseDir);
        ThemeManifest manifest = manifestResolver.resolve(tenant);
        String defaultLocale = manifest.defaultLocale();
        String baseUrl = baseUrl(tenant);

        // 默认语种渲染在根目录(prefix="");多语言时其余语种镜像在 /{locale}/ 下。
        generateForLocale(tenant, defaultLocale, defaultLocale, "", baseUrl, releaseDir, releaseDir);
        if (manifest.isMultiLanguage()) {
            for (String locale : manifest.locales()) {
                if (locale.equals(defaultLocale)) {
                    continue;
                }
                String dir = locale.toLowerCase(Locale.ROOT);
                generateForLocale(tenant, locale, defaultLocale, "/" + dir,
                        baseUrl, releaseDir.resolve(dir), releaseDir);
            }
        }
        generateSitemap(tenant, manifest, baseUrl, releaseDir);
        generateRobots(tenant, baseUrl, releaseDir);
        copyThemeImages(tenant, releaseDir);
        return releaseDir;
    }

    /**
     * 渲染单个语种的整站镜像到 {@code outputDir}。
     *
     * @param locale        渲染语种(如 zh-CN),同时驱动 Thymeleaf {@code #{}} 解析
     * @param defaultLocale 租户默认语种,用于 i18n 内容回退
     * @param localePrefix  链接前缀(""/默认 或 "/{locale}");写入 outputDir 对应
     * @param outputDir     该语种产物根目录(releaseDir 或 releaseDir/{locale})
     */
    private void generateForLocale(Tenant tenant, String locale, String defaultLocale,
                                   String localePrefix, String baseUrl,
                                   Path outputDir, Path releaseDir) throws IOException {
        generateHome(tenant, locale, defaultLocale, localePrefix, baseUrl, outputDir);
        generatePages(tenant, locale, defaultLocale, localePrefix, baseUrl, outputDir);
        generateProducts(tenant, locale, defaultLocale, localePrefix, baseUrl, outputDir, releaseDir);
        generateNews(tenant, locale, defaultLocale, localePrefix, baseUrl, outputDir, releaseDir);
    }

    /** TASK-1202:首页 → {@code index.html}。hero/cta 文案由模板 messages 键提供。 */
    public void generateHome(Tenant tenant, String locale, String defaultLocale, String localePrefix,
                             String baseUrl, Path outputDir) throws IOException {
        LocaleContext.set(new LocaleResolution(locale, "/"));
        OfflineWebContext ctx = newContext(locale);
        // 与 SiteHomeController 一致:全量 allProducts/allNews + categories,products/news 为精选子集。
        List<ProductView> allProducts = productService.listForDisplay(locale, defaultLocale).stream()
                .map(ProductView::from).toList();
        List<ArticleView> allNews = articleService.listForDisplay(locale, defaultLocale).stream()
                .map(ArticleView::from).toList();
        ctx.setVariable("allProducts", allProducts);
        ctx.setVariable("allNews", allNews);
        ctx.setVariable("products", allProducts.stream().limit(HOME_PRODUCT_LIMIT).toList());
        ctx.setVariable("news", allNews.stream().limit(HOME_NEWS_LIMIT).toList());
        ctx.setVariable("categories", categoryService.selectAll());

        // 视频:与产品同构(allVideos 全量 + videos 精选子集)。
        List<VideoView> allVideos = videoService.listForDisplay(locale, defaultLocale).stream()
                .map(VideoView::from).toList();
        ctx.setVariable("allVideos", allVideos);
        ctx.setVariable("videos", allVideos.stream().limit(HOME_VIDEO_LIMIT).toList());
        seoService.resolveView("/", null, locale, defaultLocale,
                new SeoFallback("", "", "", baseUrl + localePrefix + "/")).applyTo(ctxToModel(ctx));
        applyCommon(ctx, tenant, locale, localePrefix);
        write(outputDir, "index.html", templateEngine.process(manifestResolver.template(tenant, "index"), ctx));
    }

    /**
     * TASK-1203:静态页面 → {@code {path}/index.html}。遍历主题清单 {@link ThemeManifest#pages()},
     * 跳过动态页 {@code path ∈ {"/","/products","/news"}}(由 generateHome/generateProducts/generateNews
     * 专用生成器处理并注入 DB 列表数据,无法泛化渲染);仅渲染其余静态页(about/contact)。
     */
    public void generatePages(Tenant tenant, String locale, String defaultLocale, String localePrefix,
                               String baseUrl, Path outputDir) throws IOException {
        for (ThemePage page : manifestResolver.resolve(tenant).pages()) {
            String path = page.path();
            if (path.equals("/") || path.equals("/products") || path.equals("/news")) {
                continue; // 动态页,由专用生成器处理
            }
            LocaleContext.set(new LocaleResolution(locale, path));
            OfflineWebContext ctx = newContext(locale);
            ctx.setVariable("page", page);
            // 与 PreviewController.page 对齐:为通用页面(如 /info)注入全量产品/新闻列表,
            // 使模板内按 categorySlug 过滤的板块在线上/预览/静态三路渲染一致。
            ctx.setVariable("products", productService.listForDisplay(locale, defaultLocale).stream()
                    .map(ProductView::from).toList());
            ctx.setVariable("news", articleService.listForDisplay(locale, defaultLocale).stream()
                    .map(ArticleView::from).toList());
            // 视频:与产品同构,为通用页面注入全量视频列表(线上/预览/静态三路一致)。
            ctx.setVariable("videos", videoService.listForDisplay(locale, defaultLocale).stream()
                    .map(VideoView::from).toList());
            ctx.setVariable("categories", categoryService.selectAll());

            seoService.resolveView(path, null, locale, defaultLocale,
                    new SeoFallback(page.label(), "", "", baseUrl + localePrefix + path))
                    .applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, locale, localePrefix);
            String rel = path.startsWith("/") ? path.substring(1) : path;
            write(outputDir, rel + "/index.html", templateEngine.process(manifestResolver.template(tenant, page.template()), ctx));
        }
    }

    /** TASK-1204:产品列表(分页)+ 详情。 */
    public void generateProducts(Tenant tenant, String locale, String defaultLocale, String localePrefix,
                                 String baseUrl, Path outputDir, Path releaseDir) throws IOException {
        List<ResolvedProduct> resolved = productService.listForDisplay(locale, defaultLocale);
        List<ProductView> views = resolved.stream().map(ProductView::from).toList();
        List<ProductService.CategoryView> categories =
                productService.listCategoryViews(locale, defaultLocale);
        int pages = pageCount(views.size());

        // products/index.html → 重定向到 page/1/
        writeRedirect(outputDir, "products/index.html",
                localePrefix + "/products/page/1/", baseUrl);

        // 列表分页:products/page/{n}/index.html
        for (int n = 1; n <= pages; n++) {
            List<ProductView> slice = views.subList((n - 1) * pageSize,
                    Math.min(n * pageSize, views.size()));
            String pagePath = "/products/page/" + n + "/";
            LocaleContext.set(new LocaleResolution(locale, pagePath));
            OfflineWebContext ctx = newContext(locale);
            ctx.setVariable("products", slice);
            ctx.setVariable("allProducts", views);
            ctx.setVariable("categories", categories);
            applyPagination(ctx, n, pages, localePrefix, "/products/page/");
            seoService.resolveView("/products", null, locale, defaultLocale,
                    new SeoFallback("Products", "", "", baseUrl + localePrefix + pagePath))
                    .applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, locale, localePrefix);
            write(outputDir, "products/page/" + n + "/index.html",
                    templateEngine.process(manifestResolver.template(tenant, "products"), ctx));
        }

        // 详情:products/{slug}/index.html
        for (ResolvedProduct rp : resolved) {
            String slug = rp.product().getSlug();
            String detailPath = "/products/" + slug + "/";
            LocaleContext.set(new LocaleResolution(locale, detailPath));
            OfflineWebContext ctx = newContext(locale);
            ProductView product = ProductView.from(rp);
            ctx.setVariable("product", product);
            String fbTitle = product.seoTitle() != null ? product.seoTitle() : product.name();
            String fbDesc = product.seoDescription() != null ? product.seoDescription()
                    : (product.subtitle() != null ? product.subtitle() : "");
            seoService.resolveView(SeoConfigService.PRODUCT, rp.product().getId(), locale,
                    defaultLocale,
                    new SeoFallback(fbTitle, fbDesc, product.cover(),
                            baseUrl + localePrefix + detailPath)).applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, locale, localePrefix);
            write(outputDir, "products/" + slug + "/index.html",
                    templateEngine.process(manifestResolver.template(tenant, "product-detail"), ctx));
        }
    }

    /** TASK-1205:新闻列表(分页)+ 详情。 */
    public void generateNews(Tenant tenant, String locale, String defaultLocale, String localePrefix,
                             String baseUrl, Path outputDir, Path releaseDir) throws IOException {
        List<ResolvedArticle> resolved = articleService.listForDisplay(locale, defaultLocale);
        List<ArticleView> views = resolved.stream().map(ArticleView::from).toList();
        List<ArticleService.CategoryView> categories =
                articleService.listCategoryViews(locale, defaultLocale);
        int pages = pageCount(views.size());

        writeRedirect(outputDir, "news/index.html",
                localePrefix + "/news/page/1/", baseUrl);

        for (int n = 1; n <= pages; n++) {
            List<ArticleView> slice = views.subList((n - 1) * pageSize,
                    Math.min(n * pageSize, views.size()));
            String pagePath = "/news/page/" + n + "/";
            LocaleContext.set(new LocaleResolution(locale, pagePath));
            OfflineWebContext ctx = newContext(locale);
            ctx.setVariable("news", slice);
            ctx.setVariable("allNews", views);
            ctx.setVariable("categories", categories);
            applyPagination(ctx, n, pages, localePrefix, "/news/page/");
            seoService.resolveView("/news", null, locale, defaultLocale,
                    new SeoFallback("News", "", "", baseUrl + localePrefix + pagePath))
                    .applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, locale, localePrefix);
            write(outputDir, "news/page/" + n + "/index.html",
                    templateEngine.process(manifestResolver.template(tenant, "news"), ctx));
        }

        for (ResolvedArticle ra : resolved) {
            String slug = ra.article().getSlug();
            String detailPath = "/news/" + slug + "/";
            LocaleContext.set(new LocaleResolution(locale, detailPath));
            OfflineWebContext ctx = newContext(locale);
            ArticleView article = ArticleView.from(ra);
            ctx.setVariable("article", article);
            String fbTitle = article.seoTitle() != null ? article.seoTitle() : article.title();
            String fbDesc = article.seoDescription() != null ? article.seoDescription()
                    : (article.summary() != null ? article.summary() : "");
            seoService.resolveView(SeoConfigService.ARTICLE, ra.article().getId(), locale,
                    defaultLocale,
                    new SeoFallback(fbTitle, fbDesc, article.cover(),
                            baseUrl + localePrefix + detailPath)).applyTo(ctxToModel(ctx));
            applyCommon(ctx, tenant, locale, localePrefix);
            write(outputDir, "news/" + slug + "/index.html",
                    templateEngine.process(manifestResolver.template(tenant, "news-detail"), ctx));
        }
    }

    /** TASK-1206:sitemap.xml——含各语种镜像 URL(默认语种无前缀,其余带 /{locale}/ 前缀)。 */
    public void generateSitemap(Tenant tenant, ThemeManifest manifest, String baseUrl, Path releaseDir) throws IOException {
        String defaultLocale = manifest.defaultLocale();
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String locale : manifest.locales()) {
            String prefix = locale.equals(defaultLocale) ? "" : "/" + locale.toLowerCase(Locale.ROOT);
            for (ThemePage page : manifest.pages()) {
                String path = page.path();
                appendUrl(sb, baseUrl + prefix + (path.equals("/") ? "/" : path + "/"));
            }
            List<ResolvedProduct> products = productService.listForDisplay(locale, defaultLocale);
            int productPages = pageCount(products.size());
            for (int n = 1; n <= productPages; n++) {
                appendUrl(sb, baseUrl + prefix + "/products/page/" + n + "/");
            }
            products.forEach(p -> appendUrl(sb,
                    baseUrl + prefix + "/products/" + esc(p.product().getSlug()) + "/"));
            List<ResolvedArticle> articles = articleService.listForDisplay(locale, defaultLocale);
            int newsPages = pageCount(articles.size());
            for (int n = 1; n <= newsPages; n++) {
                appendUrl(sb, baseUrl + prefix + "/news/page/" + n + "/");
            }
            articles.forEach(a -> appendUrl(sb,
                    baseUrl + prefix + "/news/" + esc(a.article().getSlug()) + "/"));
        }
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

    private int pageCount(int total) {
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }

    /** 装填分页变量:page/totalPages/prevLink/nextLink(链接含 localePrefix)。 */
    private void applyPagination(OfflineWebContext ctx, int page, int totalPages,
                                 String localePrefix, String basePath) {
        ctx.setVariable("page", page);
        ctx.setVariable("totalPages", totalPages);
        ctx.setVariable("prevLink", page > 1 ? localePrefix + basePath + (page - 1) + "/" : null);
        ctx.setVariable("nextLink", page < totalPages ? localePrefix + basePath + (page + 1) + "/" : null);
    }

    private Path releaseDir(String tenantCode, String version) {
        return Paths.get(wwwRoot).resolve(tenantCode.toString()).resolve("releases").resolve(version);
    }

    /**
     * 将主题 {@code images} 目录拷贝到 {@code releaseDir/themes-images/{themeId}/},
     * 使离线静态站点的 {@code <img>} 引用({@code /themes-images/{themeId}/images/...})可达。
     *
     * <p>主题素材与 theme.json/messages.json/模板同置 {@code templates/themes/{themeId}/} 下;
     * 静态发布仅渲染 HTML,二进制素材需单独拷贝。通过 classpath 模式匹配枚举
     * (兼容 exploded 目录与打包 jar),无 images 目录则跳过。
     */
    private void copyThemeImages(Tenant tenant, Path releaseDir) throws IOException {
        String themeId = themeResolver.resolveTheme(tenant);
        Resource[] resources = resourcePatternResolver.getResources(
                "classpath*:templates/themes/" + themeId + "/images/**");
        for (Resource r : resources) {
            if (!r.isReadable()) {
                continue;
            }
            String url;
            try {
                url = r.getURL().toString();
            } catch (IOException e) {
                continue;
            }
            int idx = url.indexOf("/images/");
            if (idx < 0) {
                continue;
            }
            String rel = url.substring(idx + 1); // images/.../file
            if (rel.endsWith("/")) {
                continue; // 目录,跳过
            }
            Path target = releaseDir.resolve("themes-images").resolve(themeId).resolve(rel);
            Files.createDirectories(target.getParent());
            try (InputStream in = r.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("static copy theme image: {}", target);
        }
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

    private OfflineWebContext newContext(String locale) {
        return new OfflineWebContext(Locale.forLanguageTag(locale));
    }

    /** 注入 siteName / config / currentLanguage / themeId / localePrefix / languages(等同 SiteCommonAttributes)。 */
    private void applyCommon(OfflineWebContext ctx, Tenant tenant, String locale, String localePrefix) {
        String defaultLocale = defaultLocaleOf(tenant);
        TenantConfigView config = tenantConfigService.resolveForDisplay(locale, defaultLocale).orElse(null);
        String siteName = (config != null && config.companyName() != null)
                ? config.companyName() : tenant.getName();
        ctx.setVariable("siteName", siteName);
        ctx.setVariable("config", config);
        ctx.setVariable("currentLanguage", locale);
        // themeId 供模板片段引用 ~{themes/__${themeId}__/fragments/...},与 SiteCommonAttributes 一致。
        ctx.setVariable("themeId", themeResolver.resolveTheme(tenant));
        ctx.setVariable("localePrefix", localePrefix);
        ctx.setVariable("languages", buildLanguageOptions(tenant, locale, localePrefix));
    }

    private String defaultLocaleOf(Tenant tenant) {
        try {
            return manifestResolver.resolve(tenant).defaultLocale();
        } catch (Exception e) {
            return SupportedLanguage.PLATFORM_DEFAULT;
        }
    }

    /**
     * 静态页语言切换链接。默认语种无前缀(如 {@code /products/page/1/}),
     * 其余语种带小写前缀(如 {@code /zh-cn/products/page/1/}),与前台 SiteCommonAttributes 一致。
     * 单语言主题返回空列表(language 片段整体不渲染)。
     */
    private List<LanguageOption> buildLanguageOptions(Tenant tenant, String locale, String localePrefix) {
        ThemeManifest manifest;
        try {
            manifest = manifestResolver.resolve(tenant);
        } catch (Exception e) {
            return List.of();
        }
        if (!manifest.isMultiLanguage()) {
            return List.of();
        }
        String effective = LocaleContext.get().map(LocaleResolution::effectivePath).orElse("/");
        String defaultLocale = manifest.defaultLocale();
        List<LanguageOption> options = new ArrayList<>();
        for (String lang : manifest.locales()) {
            String path = lang.equals(defaultLocale)
                    ? effective
                    : "/" + lang.toLowerCase(Locale.ROOT) + effective;
            options.add(new LanguageOption(lang, label(lang), path));
        }
        return options;
    }

    /** 语言展示文案;派生自 {@link SupportedLanguage}(新增语言改一处)。 */
    private static String label(String lang) {
        return SupportedLanguage.labelOf(lang);
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

    private void write(Path outputDir, String relativePath, String content) throws IOException {
        Path target = outputDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        // 发布产物压缩体积:HTML 删除注释 + 折叠冗余空白(sitemap.xml/robots.txt 保持原样)。
        String output = relativePath.endsWith(".html") ? HtmlMinifier.minify(content) : content;
        Files.writeString(target, output);
        log.debug("static write: {} ({} -> {} bytes)", target, content.length(), output.length());
    }

    /** meta-refresh 跳板页,把 {@code {list}/} 转向 {@code {list}/page/1/}(静态站无服务端 30x)。 */
    private void writeRedirect(Path outputDir, String relativePath, String target, String baseUrl) throws IOException {
        String html = "<!DOCTYPE html>\n<html><head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta http-equiv=\"refresh\" content=\"0; url=" + esc(target) + "\">" +
                "<link rel=\"canonical\" href=\"" + esc(baseUrl + target) + "\">" +
                "<title>Redirect</title></head><body></body></html>\n";
        write(outputDir, relativePath, html);
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
}
