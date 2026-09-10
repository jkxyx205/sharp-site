package com.rick.site.page.service;

import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0401 验收测试:页面 CRUD + i18n + 富文本清洗 + 租户隔离。
 */
@SpringBootTest
@Transactional
class SitePageServiceTest {

    @Autowired
    private SitePageService pageService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(t);
        return t;
    }

    private SitePage newAbout() {
        return SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about").status((short) 1)
                .build();
    }

    @Test
    void savePageUpsertByKey() {
        Tenant t = createTenant("pg-1");
        SitePage p1 = pageService.savePage(newAbout());
        SitePage p2 = pageService.savePage(newAbout());
        assertThat(p2.getId()).isEqualTo(p1.getId());
        assertThat(pageService.listByTenant()).hasSize(1);
    }

    @Test
    void i18nSanitizesRichTextBeforePersist() {
        Tenant t = createTenant("pg-2");
        SitePage p = pageService.savePage(newAbout());
        SitePageI18n row = SitePageI18n.builder()
                .language("en-US").title("About").cover("/img/a.jpg")
                .content("<p>hi</p><script>alert(1)</script>")
                .build();
        pageService.saveI18n(p.getId(), row);

        SitePageI18n saved = pageService.findByLanguage(p.getId(), "en-US").orElseThrow();
        assertThat(saved.getContent()).contains("<p>hi</p>").doesNotContain("<script").doesNotContain("alert");
    }

    @Test
    void i18nFallsBackToDefaultLanguage() {
        Tenant t = createTenant("pg-3");
        SitePage p = pageService.savePage(newAbout());
        pageService.saveI18n(p.getId(), SitePageI18n.builder()
                .language("en-US").title("About EN").content("<p>en</p>").build());

        // 请求 zh-CN 缺失 → 回退 en-US
        SitePageService.ResolvedPage resolved =
                pageService.resolveForDisplay("/about", "zh-CN", "en-US");
        assertThat(resolved.language()).isEqualTo("en-US");
        assertThat(resolved.i18n().getTitle()).isEqualTo("About EN");
    }

    @Test
    void i18nReturnsRequestedWhenPresent() {
        Tenant t = createTenant("pg-4");
        SitePage p = pageService.savePage(newAbout());
        pageService.saveI18n(p.getId(), SitePageI18n.builder()
                .language("zh-CN").title("关于").content("<p>中文</p>").build());
        pageService.saveI18n(p.getId(), SitePageI18n.builder()
                .language("en-US").title("About").content("<p>en</p>").build());

        SitePageService.ResolvedPage resolved =
                pageService.resolveForDisplay("/about", "zh-CN", "en-US");
        assertThat(resolved.language()).isEqualTo("zh-CN");
        assertThat(resolved.i18n().getTitle()).isEqualTo("关于");
    }

    @Test
    void tenantIsolation() {
        Tenant a = createTenant("pg-isol-a");
        SitePage pa = pageService.savePage(newAbout());
        pageService.saveI18n(pa.getId(), SitePageI18n.builder()
                .language("en-US").title("About A").content("<p>A</p>").build());

        Tenant b = createTenant("pg-isol-b");
        SitePage pb = pageService.savePage(newAbout());
        pageService.saveI18n(pb.getId(), SitePageI18n.builder()
                .language("en-US").title("About B").content("<p>B</p>").build());

        // 各租户只能看到自己的页面(按 TenantContext 隔离,tenant_id 由 DatabaseConfig 追加)
        TenantContext.set(a);
        assertThat(pageService.findByPath("/about")).map(SitePage::getId).hasValue(pa.getId());
        assertThat(pageService.findByKey("about")).map(SitePage::getId).hasValue(pa.getId());
        TenantContext.set(b);
        // B 上下文按 path 查到的是自己的 pb,而非 A 的 pa(越权读取隔离)
        assertThat(pageService.findByPath("/about")).map(SitePage::getId).hasValue(pb.getId());

        // 跨租户访问 i18n 视同不存在(selectById 按上下文隔离)
        // A 上下文试图写 B 的页面 pb → requireOwned 按上下文隔离查不到 → 抛出
        TenantContext.set(a);
        SitePageI18n attempt = SitePageI18n.builder()
                .language("zh-CN").title("hijack").content("<p>x</p>").build();
        try {
            pageService.saveI18n(pb.getId(), attempt);
            assertThat(false).as("租户A不应能写租户B的页面 i18n").isTrue();
        } catch (com.rick.common.http.exception.BizException expected) {
            // 期望抛出
        }
    }
}
