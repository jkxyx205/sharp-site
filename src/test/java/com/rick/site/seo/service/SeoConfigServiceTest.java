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
                .pageType("/about").pageId(1L).language("en-US").title("Old").build());
        seoService.save(SeoConfig.builder()
                .pageType("/about").pageId(1L).language("en-US").title("New").build());
        assertThat(seoService.find("/about", 1L, "en-US").orElseThrow().getTitle())
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

    /**
     * 站点页(/about)无 DB 行时,默认从 theme.json 的 seo_config 取:en-US 取 en-US 套,
     * 未声明语种(ar-SA)回退默认语种 en-US;keywords 也来自主题(内容回退无 keywords)。
     */
    @Test
    void resolveViewFallsBackToThemeDefault() {
        createTenant("seo-i");
        SeoView en = seoService.resolveView("/about", null, "en-US", "en-US",
                new SeoFallback("FB Title", "FB Desc", "/img/fb.jpg", "https://x/p"));
        assertThat(en.title()).isEqualTo("About Us");
        assertThat(en.description()).startsWith("A trading company");
        assertThat(en.keywords()).startsWith("about us");
        // canonical/ogImage 仍来自内容回退(主题不含这两项)
        assertThat(en.canonical()).isEqualTo("https://x/p");
        assertThat(en.ogImage()).isEqualTo("/img/fb.jpg");
        // ogTitle 级联到解析出的 title
        assertThat(en.ogTitle()).isEqualTo("About Us");

        // zh-CN 取 zh-CN 套
        SeoView zh = seoService.resolveView("/about", null, "zh-CN", "en-US",
                new SeoFallback("FB", "FB", "", "https://x/p"));
        assertThat(zh.title()).isEqualTo("关于我们");

        // 未声明语种回退默认语种 en-US
        SeoView ar = seoService.resolveView("/about", null, "ar-SA", "en-US",
                new SeoFallback("FB", "FB", "", "https://x/p"));
        assertThat(ar.title()).isEqualTo("About Us");
    }

    /** DB 有行时覆盖 theme.json 默认(仅覆盖已填字段,未填字段仍回退主题)。 */
    @Test
    void resolveViewDbOverridesTheme() {
        createTenant("seo-j");
        seoService.save(SeoConfig.builder()
                .pageType("/about").language("en-US").title("Custom About").build());
        SeoView view = seoService.resolveView("/about", null, "en-US", "en-US",
                new SeoFallback("FB", "FB", "/img/fb.jpg", "https://x/p"));
        assertThat(view.title()).isEqualTo("Custom About"); // DB 覆盖
        assertThat(view.description()).startsWith("A trading company"); // DB 未填 → 主题
    }

    /** 全字段主题回退:canonical/robots/ogTitle/ogDescription/ogImage 亦取自 theme.json(留空则回退内容)。 */
    @Test
    void resolveViewThemeAllFields() {
        createTenant("seo-k");
        SeoView view = seoService.resolveView("/about", null, "en-US", "en-US",
                new SeoFallback("FB Title", "FB Desc", "/img/fb.jpg", "https://fallback/p"));
        // theme.json ogTitle=ogDescription=主题 title/description;robots=index, follow
        assertThat(view.ogTitle()).isEqualTo("About Us");
        assertThat(view.ogDescription()).startsWith("A trading company");
        assertThat(view.robots()).isEqualTo("index, follow");
        // canonical/ogImage 在 theme.json 留空 → 回退内容回退
        assertThat(view.canonical()).isEqualTo("https://fallback/p");
        assertThat(view.ogImage()).isEqualTo("/img/fb.jpg");

        // DB 覆盖 robots 与 ogImage,其余仍回退主题
        seoService.save(SeoConfig.builder()
                .pageType("/about").language("en-US")
                .robots("noindex, follow").ogImage("/img/db.jpg").build());
        SeoView over = seoService.resolveView("/about", null, "en-US", "en-US",
                new SeoFallback("FB", "FB", "", "https://x/p"));
        assertThat(over.robots()).isEqualTo("noindex, follow");
        assertThat(over.ogImage()).isEqualTo("/img/db.jpg");
        // ogTitle 未在 DB 填 → 主题 ogTitle(About Us)
        assertThat(over.ogTitle()).isEqualTo("About Us");
    }

    @Test
    void nullPageIdForHome() {
        createTenant("seo-f");
        seoService.save(SeoConfig.builder()
                .pageType("/").language("en-US").title("Home SEO").build());
        SeoConfig found = seoService.find("/", null, "en-US").orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Home SEO");
    }

    @Test
    void isolatedPerTenant() {
        Tenant a = createTenant("seo-g");
        seoService.save(SeoConfig.builder()
                .pageType("/about").pageId(1L).language("en-US").title("A SEO").build());
        Tenant b = createTenant("seo-h");
        seoService.save(SeoConfig.builder()
                .pageType("/about").pageId(1L).language("en-US").title("B SEO").build());
        TenantContext.set(a);
        assertThat(seoService.find("/about", 1L, "en-US").orElseThrow().getTitle())
                .isEqualTo("A SEO");
        TenantContext.set(b);
        assertThat(seoService.find("/about", 1L, "en-US").orElseThrow().getTitle())
                .isEqualTo("B SEO");
    }
}
