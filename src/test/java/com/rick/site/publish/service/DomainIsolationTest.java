package com.rick.site.publish.service;

import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService;
import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.page.service.SitePageService;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService;
import com.rick.site.publish.entity.PublishRecord;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-1403 验收测试:域名隔离。
 *
 * <p>两个租户各自发布静态站点,断言:
 * <ul>
 *   <li>每租户 {@code current} 符号链接解析到各自 tenantId 子目录的 release</li>
 *   <li>两租户的 release 目录路径不相交(不同 tenantId 子目录)→
 *       Tenant A 无法访问 Tenant B 的静态文件(Nginx 按 root 隔离)</li>
 * </ul>
 *
 * <p>结构性隔离:静态站点按 {@code {www-root}/{tenantId}/current} 落盘,
 * 每租户独立子目录;Nginx server 块 root 硬绑定单一租户目录(TASK-1402),
 * 不存在跨租户目录穿越路径。
 */
@SpringBootTest
@Transactional
class DomainIsolationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void wwwRoot(DynamicPropertyRegistry reg) {
        reg.add("sharp.site.www-root", () -> tempDir.toString());
    }

    @Autowired
    private PublishService publishService;
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

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setup() {
        tenantA = tenantService.save(Tenant.builder()
                .code("iso-a").name("Iso A").themeId("modern").build());
        TenantContext.set(tenantA);
        domainService.add("a-iso.example.com", true);
        seedContent();

        tenantB = tenantService.save(Tenant.builder()
                .code("iso-b").name("Iso B").themeId("modern").build());
        TenantContext.set(tenantB);
        domainService.add("b-iso.example.com", true);
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
                .slug("widget").status((short) 1).sort(0).build());
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget").content("<p>d</p>").build());
    }

    @Test
    void eachTenantCurrentResolvesToOwnSubdir() throws Exception {
        TenantContext.set(tenantA);
        PublishRecord recA = publishService.publish();
        assertEquals("v001", recA.getVersion());

        TenantContext.set(tenantB);
        PublishRecord recB = publishService.publish();
        assertEquals("v001", recB.getVersion());

        Path currentA = publishService.currentPath(tenantA.getId());
        Path currentB = publishService.currentPath(tenantB.getId());

        assertTrue(Files.isSymbolicLink(currentA), "A 的 current 应为符号链接");
        assertTrue(Files.isSymbolicLink(currentB), "B 的 current 应为符号链接");

        Path releaseA = Files.readSymbolicLink(currentA);
        Path releaseB = Files.readSymbolicLink(currentB);

        // 各自 release 路径包含各自 tenantId
        assertTrue(releaseA.toString().contains(tenantA.getId().toString()),
                "A release 在 A 的 tenantId 子目录下: " + releaseA);
        assertTrue(releaseB.toString().contains(tenantB.getId().toString()),
                "B release 在 B 的 tenantId 子目录下: " + releaseB);

        // 关键隔离断言:两 release 目录不相交(不同 tenantId 子目录)
        assertNotEquals(releaseA, releaseB, "两租户 release 路径必须不同");

        // 各自 release 内有 index.html,且互不重叠(父目录不同)
        assertTrue(Files.exists(releaseA.resolve("index.html")));
        assertTrue(Files.exists(releaseB.resolve("index.html")));
        assertNotEquals(releaseA.getParent(), releaseB.getParent(),
                "release 父目录(tenantId 子目录)必须不同 → 租户隔离");
    }
}
