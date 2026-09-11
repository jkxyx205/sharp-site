package com.rick.site.seo.service;

import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.dto.SeoView;
import com.rick.site.seo.entity.SeoConfig;
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
 * TASK-1001 验收测试:seo_config CRUD、(page_type,page_id,language) 幂等 upsert、
 * 语言回退、租户隔离,以及 resolveView 的 seo_config/回退融合。
 */
@SpringBootTest
@Transactional
class SeoConfigServiceTest {

    @Autowired
    private SeoConfigService seoService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").build());
        TenantContext.set(t);
        return t;
    }

    @Test
    void saveAndFind() {
        Tenant a = createTenant("seo-a");
        SeoConfig saved = seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PRODUCT).pageId(123L).language("en-US")
                .title("Widget SEO").description("Desc").keywords("k1,k2")
                .canonical("https://a.example.com/products/widget")
                .robots("index, follow").ogTitle("OG").ogImage("/img/x.jpg").build());
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(a.getId());
        SeoConfig found = seoService.find(SeoConfigService.PRODUCT, 123L, "en-US").orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Widget SEO");
        assertThat(found.getKeywords()).isEqualTo("k1,k2");
    }

    @Test
    void upsertUpdatesSameRow() {
        createTenant("seo-b");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PAGE).pageId(1L).language("en-US").title("Old").build());
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PAGE).pageId(1L).language("en-US").title("New").build());
        assertThat(seoService.find(SeoConfigService.PAGE, 1L, "en-US").orElseThrow().getTitle())
                .isEqualTo("New");
    }

    @Test
    void resolveFallsBackToDefaultLanguage() {
        createTenant("seo-c");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PRODUCT).pageId(2L).language("en-US").title("EN").build());
        // 只存了 en-US;请求 zh-CN → 回退默认语言 en-US
        SeoConfig resolved = seoService.resolve(SeoConfigService.PRODUCT, 2L, "zh-CN", "en-US");
        assertThat(resolved).isNotNull();
        assertThat(resolved.getTitle()).isEqualTo("EN");
        assertThat(resolved.getLanguage()).isEqualTo("en-US");
    }

    @Test
    void resolveReturnsNullWhenAbsent() {
        createTenant("seo-c2");
        SeoConfig resolved = seoService.resolve(SeoConfigService.PRODUCT, 99L, "zh-CN", "en-US");
        assertThat(resolved).isNull();
    }

    @Test
    void resolveViewUsesConfigOverFallback() {
        createTenant("seo-d");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PRODUCT).pageId(3L).language("en-US")
                .title("Config Title").description("Config Desc").ogImage("/img/og.jpg").build());
        SeoView view = seoService.resolveView(SeoConfigService.PRODUCT, 3L, "en-US", "en-US",
                new SeoFallback("Fallback Title", "Fallback Desc", "/img/fb.jpg", "https://x/p"));
        assertThat(view.title()).isEqualTo("Config Title");
        assertThat(view.description()).isEqualTo("Config Desc");
        assertThat(view.canonical()).isEqualTo("https://x/p"); // config 无 canonical → 回退
        assertThat(view.ogTitle()).isEqualTo("Config Title"); // config 无 ogTitle → 回退 title
        assertThat(view.ogImage()).isEqualTo("/img/og.jpg");
    }

    @Test
    void resolveViewFallsBackWhenNoConfig() {
        createTenant("seo-e");
        SeoView view = seoService.resolveView(SeoConfigService.PRODUCT, 4L, "en-US", "en-US",
                new SeoFallback("FB Title", "FB Desc", "/img/fb.jpg", "https://x/p"));
        assertThat(view.title()).isEqualTo("FB Title");
        assertThat(view.description()).isEqualTo("FB Desc");
        assertThat(view.keywords()).isEmpty();
        assertThat(view.robots()).isEqualTo("index, follow");
        assertThat(view.canonical()).isEqualTo("https://x/p");
        assertThat(view.ogImage()).isEqualTo("/img/fb.jpg");
        assertThat(view.ogTitle()).isEqualTo("FB Title");
    }

    @Test
    void nullPageIdForHome() {
        createTenant("seo-f");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.HOME).language("en-US").title("Home SEO").build());
        SeoConfig found = seoService.find(SeoConfigService.HOME, null, "en-US").orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Home SEO");
    }

    @Test
    void isolatedPerTenant() {
        Tenant a = createTenant("seo-g");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PAGE).pageId(1L).language("en-US").title("A SEO").build());
        Tenant b = createTenant("seo-h");
        seoService.save(SeoConfig.builder()
                .pageType(SeoConfigService.PAGE).pageId(1L).language("en-US").title("B SEO").build());
        TenantContext.set(a);
        assertThat(seoService.find(SeoConfigService.PAGE, 1L, "en-US").orElseThrow().getTitle())
                .isEqualTo("A SEO");
        TenantContext.set(b);
        assertThat(seoService.find(SeoConfigService.PAGE, 1L, "en-US").orElseThrow().getTitle())
                .isEqualTo("B SEO");
    }
}
