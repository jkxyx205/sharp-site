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
import com.rick.site.publish.entity.PublishRecord;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-1302/1303/1304 验收测试:
 * 发布成功原子切换 current→v001;v002 失败时 current 不变(仍指 v001)+ 记录 FAILED。
 */
@SpringBootTest
@Transactional
class PublishServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void wwwRoot(DynamicPropertyRegistry reg) {
        reg.add("sharp.site.www-root", () -> tempDir.toString());
    }

    @Autowired
    private PublishService publishService;
    @Autowired
    private PublishRecordService recordService;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private HomeSectionService homeSectionService;
    @Autowired
    private SitePageService pageService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ArticleService articleService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("pub").name("Publish Co").themeId("modern").build());
        TenantContext.set(tenant);
        seedContent();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private void seedContent() {
        HomeSection hero = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("hero").sort(0).enabled((short) 1).build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome").subtitle("We build").build());

        SitePage about = pageService.savePage(SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about").status((short) 1).build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("en-US").title("About").content("<p>about</p>").build());

        Product p = productService.saveProduct(Product.builder()
                .slug("widget-a").status((short) 1).sort(0).build());
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget A").content("<p>d</p>").build());

        Article a = articleService.saveArticle(Article.builder()
                .slug("fair").status((short) 1).sort(0).build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("Fair").content("<p>n</p>").build());
    }

    @Test
    void publishSuccessAtomicallySwitchesCurrent() throws Exception {
        PublishRecord rec = publishService.publish();
        assertEquals("v001", rec.getVersion());
        assertEquals(PublishRecordService.STATUS_SUCCESS, rec.getStatus());

        Path current = publishService.currentPath(tenant.getId());
        assertTrue(Files.isSymbolicLink(current), "current 应为符号链接");
        Path target = Files.readSymbolicLink(current);
        assertTrue(target.toString().contains("v001"), "current → v001");
        // release 目录文件齐全
        assertTrue(Files.exists(target.resolve("index.html")));
        assertTrue(Files.exists(target.resolve("products/widget-a/index.html")));
        assertTrue(Files.exists(target.resolve("sitemap.xml")));
        assertTrue(Files.exists(target.resolve("robots.txt")));

        assertEquals("v002", recordService.nextVersion(), "下一次版本自增");
    }

    @Test
    void publishFailureLeavesCurrentUnchanged() throws Exception {
        // 首次发布成功:current → v001
        publishService.publish();
        Path current = publishService.currentPath(tenant.getId());
        Path targetBefore = Files.readSymbolicLink(current);
        assertTrue(targetBefore.toString().contains("v001"));

        // 制造失败:将页面模板改为不存在的模板,生成阶段抛 TemplateProcessingException
        SitePage about = pageService.listByTenant().get(0);
        about.setTemplate("themes/modern/__nonexistent__");
        pageService.savePage(about);

        // v002 发布应失败,抛 BizException
        assertThrows(Exception.class, () -> publishService.publish());

        // current 仍指向 v001,未被半切换
        Path targetAfter = Files.readSymbolicLink(current);
        assertEquals(targetBefore, targetAfter, "失败不切换 current");

        // 最新记录为 v002 / FAILED
        PublishRecord latest = recordService.latest().orElseThrow();
        assertEquals("v002", latest.getVersion());
        assertEquals(PublishRecordService.STATUS_FAILED, latest.getStatus());
        assertTrue(latest.getErrorMessage() != null && !latest.getErrorMessage().isBlank(), "记录错误信息");
    }

    @Test
    void recordsPersistAndListByTenant() throws Exception {
        publishService.publish();
        List<PublishRecord> records = recordService.listByTenant();
        assertEquals(1, records.size());
    }
}
