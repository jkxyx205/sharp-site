package com.rick.site.home.service;

import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService.ResolvedSection;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0501 验收测试:首页区块 CRUD + i18n + 富文本清洗 + 租户隔离。
 */
@SpringBootTest
@Transactional
class HomeSectionServiceTest {

    @Autowired
    private HomeSectionService homeSectionService;

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

    private HomeSection newSection(String key, int sort) {
        return HomeSection.builder().sectionKey(key).sort(sort).enabled((short) 1).build();
    }

    @Test
    void saveSectionUpsertByKey() {
        Tenant t = createTenant("hs-1");
        HomeSection s1 = homeSectionService.saveSection(newSection("hero", 0));
        HomeSection s2 = homeSectionService.saveSection(newSection("hero", 1));
        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(homeSectionService.listByTenant()).hasSize(1);
    }

    @Test
    void i18nSanitizesRichText() {
        Tenant t = createTenant("hs-2");
        HomeSection s = homeSectionService.saveSection(newSection("company", 1));
        homeSectionService.saveI18n(s.getId(), HomeSectionI18n.builder()
                .language("en-US").title("About").subtitle("sub")
                .content("<p>ok</p><script>evil()</script>").build());
        HomeSectionI18n saved = homeSectionService.findByLanguage(s.getId(), "en-US").orElseThrow();
        assertThat(saved.getContent()).contains("<p>ok</p>").doesNotContain("<script").doesNotContain("evil");
    }

    @Test
    void resolveForDisplayFallsBackToDefault() {
        Tenant t = createTenant("hs-3");
        HomeSection hero = homeSectionService.saveSection(newSection("hero", 0));
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome").subtitle("We build").build());
        HomeSection company = homeSectionService.saveSection(newSection("company", 1));
        homeSectionService.saveI18n(company.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Company").content("<p>us</p>").build());

        // 请求 zh-CN 缺失 → 回退 en-US
        Map<String, ResolvedSection> sections =
                homeSectionService.resolveForDisplay("zh-CN", "en-US");
        assertThat(sections).containsKeys("hero", "company");
        assertThat(sections.get("hero").language()).isEqualTo("en-US");
        assertThat(sections.get("hero").i18n().getTitle()).isEqualTo("Welcome");
    }

    @Test
    void disabledSectionsExcluded() {
        Tenant t = createTenant("hs-4");
        homeSectionService.saveSection(newSection("hero", 0));
        HomeSection off = homeSectionService.saveSection(newSection("advantages", 2));
        off.setEnabled((short) 0);
        homeSectionService.saveSection(off);

        Map<String, ResolvedSection> sections =
                homeSectionService.resolveForDisplay("en-US", "en-US");
        assertThat(sections).containsKey("hero").doesNotContainKey("advantages");
    }
}
