package com.rick.site.catalog.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.entity.CategoryI18n;
import com.rick.site.catalog.service.CategoryService.ResolvedCategory;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-1504(CMS)验收:分类 CRUD + i18n + 回退 + 展示 + 租户隔离。
 *
 * <p>分类供 PRODUCT / NEWS 共用,type 区分;按 (type, slug) 幂等 upsert,
 * 按 (category_id, language) 幂等 upsert i18n。
 */
@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

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

    private Category newCategory(String slug) {
        return Category.builder()
                .type("PRODUCT").slug(slug).sort(0).status((short) 1).build();
    }

    @Test
    void upsertByTypeAndSlug() {
        Tenant t = createTenant("cat-1");
        Category c1 = categoryService.saveCategory(newCategory("tools"));
        Category c2 = categoryService.saveCategory(newCategory("tools"));
        assertThat(c2.getId()).isEqualTo(c1.getId());
        assertThat(categoryService.listByType("PRODUCT")).hasSize(1);
    }

    @Test
    void i18nUpsertByLanguageAndFallback() {
        Tenant t = createTenant("cat-2");
        Category c = categoryService.saveCategory(newCategory("gear"));
        categoryService.saveI18n(c.getId(), CategoryI18n.builder()
                .language("en-US").name("Gear").description("en desc").build());

        // 幂等:同 (categoryId, language) 更新而非新增
        categoryService.saveI18n(c.getId(), CategoryI18n.builder()
                .language("en-US").name("Gear Pro").build());
        assertThat(categoryService.findByLanguage(c.getId(), "en-US"))
                .map(CategoryI18n::getName).hasValue("Gear Pro");

        // 回退:请求 zh-CN 但只有 en-US → 命中 en-US,语言标记为默认 en-US
        Map<String, ResolvedCategory> resolved =
                categoryService.resolveForDisplay("PRODUCT", "zh-CN", "en-US");
        assertThat(resolved).containsKey("gear");
        assertThat(resolved.get("gear").language()).isEqualTo("en-US");
        assertThat(resolved.get("gear").i18n().getName()).isEqualTo("Gear Pro");
    }

    @Test
    void listEnabledExcludesDisabled() {
        Tenant t = createTenant("cat-3");
        Category on = categoryService.saveCategory(newCategory("on"));
        Category off = newCategory("off");
        off.setStatus((short) 0);
        categoryService.saveCategory(off);

        assertThat(categoryService.listEnabled("PRODUCT"))
                .map(Category::getSlug).contains("on").doesNotContain("off");
    }

    @Test
    void tenantIsolation() {
        Tenant a = createTenant("cat-isol-a");
        Category ca = categoryService.saveCategory(newCategory("shared"));
        categoryService.saveI18n(ca.getId(), CategoryI18n.builder()
                .language("en-US").name("A's cat").build());

        Tenant b = createTenant("cat-isol-b");
        Category cb = categoryService.saveCategory(newCategory("shared"));
        categoryService.saveI18n(cb.getId(), CategoryI18n.builder()
                .language("en-US").name("B's cat").build());

        // 各租户按 slug 查到自己的分类
        TenantContext.set(a);
        assertThat(categoryService.findBySlug("PRODUCT", "shared"))
                .map(Category::getId).hasValue(ca.getId());
        TenantContext.set(b);
        assertThat(categoryService.findBySlug("PRODUCT", "shared"))
                .map(Category::getId).hasValue(cb.getId());

        // 跨租户写 i18n 视同不存在(selectById 按上下文隔离)
        TenantContext.set(a);
        assertThatThrownBy(() -> categoryService.saveI18n(cb.getId(), CategoryI18n.builder()
                .language("zh-CN").name("hijack").build()))
                .isInstanceOf(BizException.class);
    }
}
