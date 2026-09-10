package com.rick.site;

import com.rick.site.admin.service.AdminUserService;
import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.entity.CategoryI18n;
import com.rick.site.catalog.service.CategoryService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 测试数据种子(idempotent)。仅在 {@code seed} profile 下运行:
 * <pre>
 * ./gradlew bootRun --args="--spring.profiles.active=seed --spring.main.web-application-type=none"
 * </pre>
 * 重复执行安全:租户/域名/管理员/内容均按唯一键 upsert 或预检跳过。
 *
 * <p>创建:demo 租户(modern 主题)+ localhost 主域名 + 管理员 admin/111111 + 示例内容。
 * §20:不日志输出密码。
 *
 * @author Rick.Xu
 */
@Component
@Profile("seed")
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private static final String TENANT_CODE = "demo";
    private static final String TENANT_NAME = "Demo Site";
    private static final String THEME_ID = "modern";
    private static final String DOMAIN = "localhost";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "111111";

    private final TenantService tenantService;
    private final TenantDomainService domainService;
    private final AdminUserService adminUserService;
    private final HomeSectionService homeSectionService;
    private final SitePageService pageService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final ArticleService articleService;

    public SeedDataRunner(TenantService tenantService,
                          TenantDomainService domainService,
                          AdminUserService adminUserService,
                          HomeSectionService homeSectionService,
                          SitePageService pageService,
                          CategoryService categoryService,
                          ProductService productService,
                          ArticleService articleService) {
        this.tenantService = tenantService;
        this.domainService = domainService;
        this.adminUserService = adminUserService;
        this.homeSectionService = homeSectionService;
        this.pageService = pageService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.articleService = articleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Tenant tenant = ensureTenant();
        TenantContext.set(tenant);
        try {
            ensureDomain();
            ensureAdmin();
            seedContent();
            log.info("测试数据已就绪:租户={} 域名={} 管理员={}/{}",
                    TENANT_CODE, DOMAIN, ADMIN_USERNAME, ADMIN_PASSWORD);
        } finally {
            TenantContext.clear();
        }
    }

    private Tenant ensureTenant() {
        return tenantService.findByCode(TENANT_CODE).orElseGet(() ->
                tenantService.save(Tenant.builder()
                        .code(TENANT_CODE).name(TENANT_NAME).themeId(THEME_ID)
                        .defaultLanguage("en-US").build()));
    }

    private void ensureDomain() {
        if (domainService.findByDomain(DOMAIN).isEmpty()) {
            domainService.add(DOMAIN, true);
            log.info("已绑定主域名: {}", DOMAIN);
        }
    }

    private void ensureAdmin() {
        if (adminUserService.findByUsername(ADMIN_USERNAME).isEmpty()) {
            adminUserService.create(ADMIN_USERNAME, ADMIN_PASSWORD);
            log.info("已创建管理员: {}", ADMIN_USERNAME);
        }
    }

    /** 示例内容(modern 主题首页/页面/产品/新闻),幂等 upsert。 */
    private void seedContent() {
        // 首页 hero 区块(en + zh)
        HomeSection hero = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("hero").sort(0).enabled((short) 1).build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome to Demo")
                .subtitle("Premium products for global trade")
                .content("<p>We supply quality goods to partners worldwide.</p>").build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("zh-CN").title("欢迎来到 Demo")
                .subtitle("优质产品 · 全球贸易")
                .content("<p>我们向全球合作伙伴供应优质商品。</p>").build());

        // 关于页面
        SitePage about = pageService.savePage(SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about")
                .status((short) 1).build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("en-US").title("About Us")
                .content("<p>Demo is a trading company connecting manufacturers and buyers globally.</p>").build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("zh-CN").title("关于我们")
                .content("<p>Demo 是一家连接制造商与全球采购商的贸易公司。</p>").build());

        // 分类 + 产品
        Category electronics = categoryService.saveCategory(Category.builder()
                .type("PRODUCT").slug("electronics").sort(0).status((short) 1).build());
        categoryService.saveI18n(electronics.getId(), CategoryI18n.builder()
                .language("en-US").name("Electronics").description("Consumer electronics").build());
        categoryService.saveI18n(electronics.getId(), CategoryI18n.builder()
                .language("zh-CN").name("电子产品").description("消费电子").build());

        Product widget = productService.saveProduct(Product.builder()
                .slug("smart-widget").cover("/img/widget.jpg")
                .status((short) 1).sort(0).build());
        widget.setCategoryId(electronics.getId());
        productService.saveProduct(widget);
        productService.saveI18n(widget.getId(), ProductI18n.builder()
                .language("en-US").name("Smart Widget").subtitle("Pro Edition")
                .description("A connected smart widget.").content("<p>Feature-rich and reliable.</p>")
                .specificationJson("{\"weight\":\"0.5kg\",\"color\":\"black\"}")
                .seoTitle("Smart Widget").seoDescription("Smart widget for global buyers").build());
        productService.saveI18n(widget.getId(), ProductI18n.builder()
                .language("zh-CN").name("智能小工具").subtitle("专业版")
                .description("一款联网智能小工具。").content("<p>功能丰富,可靠耐用。</p>")
                .specificationJson("{\"weight\":\"0.5kg\",\"color\":\"黑色\"}")
                .seoTitle("智能小工具").seoDescription("面向全球采购的智能小工具").build());

        // 新闻
        Article news = articleService.saveArticle(Article.builder()
                .slug("company-launched").cover("/img/news.jpg").author("Demo")
                .status((short) 1).sort(0).build());
        articleService.saveI18n(news.getId(), ArticleI18n.builder()
                .language("en-US").title("Demo Website Launched")
                .summary("Our new site is live.").content("<p>Welcome to our newly launched website.</p>")
                .seoTitle("Demo Website Launched").seoDescription("Demo site launch news").build());
        articleService.saveI18n(news.getId(), ArticleI18n.builder()
                .language("zh-CN").title("Demo 网站上线")
                .summary("我们的新网站正式上线。").content("<p>欢迎访问我们全新上线的网站。</p>")
                .seoTitle("Demo 网站上线").seoDescription("Demo 网站上线新闻").build());

        log.info("示例内容已写入(hero / about / 产品 / 新闻, en-US + zh-CN)");
    }
}
