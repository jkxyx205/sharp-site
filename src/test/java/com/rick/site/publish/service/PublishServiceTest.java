package com.rick.site.publish.service;

import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
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

        // 制造失败:在 v002 release 路径占位一个普通文件,使 generate() 的
        // Files.createDirectories(releases/v002) 抛异常(目标存在且非目录)。
        // 不再依赖 site_page 改模板来注入失败。
        Path blocker = tempDir.resolve(tenant.getId().toString())
                .resolve("releases").resolve("v002");
        Files.createDirectories(blocker.getParent());
        Files.createFile(blocker);

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
