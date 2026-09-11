package com.rick.site.publish.service;

import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.page.service.SitePageService;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-1201..1206 验收测试:StaticSiteGenerator 生成首页/页面/产品/新闻/sitemap/robots,
 * 输出到 {wwwRoot}/{tenantId}/releases/{version}/。发布站点默认 index,follow(非 noindex)。
 */
@SpringBootTest
@Transactional
class StaticSiteGeneratorTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void wwwRoot(DynamicPropertyRegistry reg) {
        reg.add("sharp.site.www-root", () -> tempDir.toString());
    }

    @Autowired
    private StaticSiteGenerator generator;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private HomeSectionService homeSectionService;
    @Autowired
    private SitePageService pageService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ArticleService articleService;

    private Tenant tenant;
    private Path releaseDir;

    @BeforeEach
    void setup() throws Exception {
        tenant = tenantService.save(Tenant.builder()
                .code("stg").name("Static Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("www.example.com", true);

        HomeSection hero = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("hero").sort(0).enabled((short) 1).build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome Home").subtitle("We build great things").build());
        HomeSection company = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("company").sort(1).enabled((short) 1).build());
        homeSectionService.saveI18n(company.getId(), HomeSectionI18n.builder()
                .language("en-US").title("About Us").content("<p>Founded in 2010</p>").build());

        SitePage about = pageService.savePage(SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about").status((short) 1).build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("en-US").title("About Us").content("<p>We are great</p>").build());

        Product p = productService.saveProduct(Product.builder()
                .slug("widget-a").status((short) 1).sort(0).build());
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget A").subtitle("Best widget").content("<p>Detail A</p>").build());

        Article a = articleService.saveArticle(Article.builder()
                .slug("canton-fair").author("Ed").status((short) 1).sort(0).build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("Canton Fair").summary("Fair news").content("<p>News body</p>").build());

        releaseDir = generator.generate("v1");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private String read(String rel) throws Exception {
        return Files.readString(releaseDir.resolve(rel));
    }

    @Test
    void generatesHomeIndexHtml() throws Exception {
        String html = read("index.html");
        assertTrue(html.contains("Welcome Home"), "home hero title");
        assertTrue(html.contains("Founded in 2010"), "company teaser");
        assertTrue(html.contains("/products"), "root-relative product link");
        // 发布站点默认 index,follow(非预览 noindex)
        assertTrue(html.contains("index, follow"));
        // 主题样式内联进模板,发布产物自带 <style>;不再外部引用 static/themes/modern/css
        // (静态站点由 Nginx 提供文件,不经 Spring app,外部 CSS 路径会 404 → 样式丢失)
        assertTrue(html.contains("<style"), "inline theme style");
        assertTrue(!html.contains("/themes/modern/css/style.css"), "no external css link");
        // ThemeMessageSource 按 themeId+locale 解析 #{key}(en-US 默认语种),
        // 缺键会渲染 ??key??,故断言无未解析标记且含预期文案。
        assertTrue(!html.contains("??"), "no unresolved message markers");
        assertTrue(html.contains("Featured Products"), "home.featured.title message");
        assertTrue(html.contains("Latest News"), "home.news.title message");
        assertTrue(html.contains("View Products"), "home.products.btn message");
    }

    @Test
    void generatesPages() throws Exception {
        String about = read("about/index.html");
        assertTrue(about.contains("About Us"));
        assertTrue(about.contains("We are great"));
    }

    @Test
    void generatesProducts() throws Exception {
        assertTrue(Files.exists(releaseDir.resolve("products/index.html")));
        String detail = read("products/widget-a/index.html");
        assertTrue(detail.contains("Widget A"));
        assertTrue(detail.contains("Detail A"));
    }

    @Test
    void generatesNews() throws Exception {
        assertTrue(Files.exists(releaseDir.resolve("news/index.html")));
        String detail = read("news/canton-fair/index.html");
        assertTrue(detail.contains("Canton Fair"));
        assertTrue(detail.contains("News body"));
    }

    @Test
    void generatesSitemapAndRobots() throws Exception {
        String sitemap = read("sitemap.xml");
        assertTrue(sitemap.contains("<urlset"));
        assertTrue(sitemap.contains("https://www.example.com/"));
        assertTrue(sitemap.contains("https://www.example.com/about"));
        assertTrue(sitemap.contains("https://www.example.com/products/widget-a"));
        assertTrue(sitemap.contains("https://www.example.com/news/canton-fair"));

        String robots = read("robots.txt");
        assertTrue(robots.contains("Allow: /"));
        assertTrue(robots.contains("Sitemap: https://www.example.com/sitemap.xml"));
    }

    @Test
    void releaseDirLayout() {
        assertTrue(releaseDir.toString().startsWith(tempDir.toString()));
        assertTrue(releaseDir.endsWith("releases/v1"));
        assertEquals(tempDir.resolve(tenant.getId().toString())
                        .resolve("releases").resolve("v1"), releaseDir);
    }
}
