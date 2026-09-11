package com.rick.site;

import com.rick.site.admin.service.AdminUserService;
import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.entity.CategoryI18n;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 测试数据种子(idempotent)。仅当系统属性 {@code sharp.seed=true} 时执行,
 * 故 {@code ./gradlew build} 默认跳过;手动执行:
 * <pre>
 * ./gradlew test --tests "com.rick.site.SeedDataTest" -PsharpSeed
 * </pre>
 *
 * <p>采用 {@code @SpringBootTest}(与 142 项测试同上下文),避免 bootRun 启动期
 * sharp-meta 初始化与 {@code EntityDAOManager.tableNameDAOMap} 的时序问题。
 * 非 {@code @Transactional} —— 数据需持久化。重复执行安全:租户/域名/管理员/
 * 内容均按唯一键 upsert 或预检跳过。
 *
 * <p>创建:demo 租户(modern 主题)+ demo.localhost 主域名 + 管理员 admin/111111
 * + 示例内容(en-US + zh-CN)。页面(about/contact)与首页区块(hero/cta)文案由
 * 主题模板 + {@code messages.json} 维护,不再播种 site_page / home_section。
 * 域名用 demo.localhost(自动解析到 127.0.0.1),不占用测试使用的 localhost /
 * *.example.com,保持构建绿色。§20:不日志输出密码。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "sharp.seed", matches = "true")
class SeedDataTest {

    private static final String TENANT_CODE = "demo";
    private static final String TENANT_NAME = "Demo Site";
    private static final String THEME_ID = "modern";
    private static final String DOMAIN = "demo.localhost";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "111111";

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ArticleService articleService;

    private Tenant tenant;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void seedDemoData() {
        tenant = tenantService.findByCode(TENANT_CODE).orElseGet(() ->
                tenantService.save(Tenant.builder()
                        .code(TENANT_CODE).name(TENANT_NAME).themeId(THEME_ID)
                        .build()));
        TenantContext.set(tenant);

        ensureDomain();
        ensureAdmin();
        seedContent();
    }

    private void ensureDomain() {
        if (domainService.findByDomain(DOMAIN).isEmpty()) {
            domainService.add(DOMAIN, true);
        }
    }

    private void ensureAdmin() {
        if (adminUserService.findByUsername(ADMIN_USERNAME).isEmpty()) {
            adminUserService.create(ADMIN_USERNAME, ADMIN_PASSWORD);
        }
    }

    private void seedContent() {
        // 幂等:各服务按唯一键 upsert(同语言 i18n 二次保存走 UPDATE)。
        // jsonb 列 UPDATE 已由 sql/schema.sql 的 varchar→jsonb 赋值转换修复。
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
    }
}
