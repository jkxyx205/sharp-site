package com.rick.site.product.service;

import com.rick.site.catalog.entity.Category;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService.ResolvedProduct;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0601/0602/0603/0605 验收:分类 + 产品 CRUD + i18n + 富文本清洗 + 规格 JSONB + 租户隔离。
 */
@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

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

    private Category createCategory(String slug) {
        return categoryService.saveCategory(Category.builder()
                .type("PRODUCT").slug(slug).sort(0).status((short) 1).build());
    }

    private Product newProduct(String slug) {
        return Product.builder().slug(slug).cover("/img/" + slug + ".jpg")
                .status((short) 1).sort(0).build();
    }

    @Test
    void productUpsertBySlug() {
        Tenant t = createTenant("prod-1");
        Product p1 = productService.saveProduct(newProduct("widget-a"));
        Product p2 = productService.saveProduct(newProduct("widget-a"));
        assertThat(p2.getId()).isEqualTo(p1.getId());
        assertThat(productService.listByTenant()).hasSize(1);
    }

    @Test
    void i18nSanitizesContentAndStoresSpecificationJson() {
        Tenant t = createTenant("prod-2");
        Category c = createCategory("tools");
        Product p = newProduct("widget-b");
        p.setCategoryId(c.getId());
        productService.saveProduct(p);
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget B").subtitle("Pro")
                .description("desc").content("<p>ok</p><script>evil()</script>")
                .specificationJson("{\"weight\":\"1kg\"}")
                .seoTitle("Widget B SEO").seoDescription("desc seo").build());

        ProductI18n saved = productService.findByLanguage(p.getId(), "en-US").orElseThrow();
        assertThat(saved.getContent()).contains("<p>ok</p>").doesNotContain("<script").doesNotContain("evil");
        assertThat(saved.getSpecificationJson()).contains("weight");
        assertThat(saved.getSeoTitle()).isEqualTo("Widget B SEO");
    }

    @Test
    void i18nFallsBackToDefaultLanguage() {
        Tenant t = createTenant("prod-3");
        Product p = productService.saveProduct(newProduct("widget-c"));
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget C").content("<p>en</p>").build());

        ResolvedProduct resolved = productService.resolveForDisplay("widget-c", "zh-CN", "en-US");
        assertThat(resolved.language()).isEqualTo("en-US");
        assertThat(resolved.i18n().getName()).isEqualTo("Widget C");
    }

    @Test
    void listForDisplayResolvesEachProduct() {
        Tenant t = createTenant("prod-4");
        Product a = productService.saveProduct(newProduct("x-1"));
        Product b = productService.saveProduct(newProduct("x-2"));
        productService.saveI18n(a.getId(), ProductI18n.builder()
                .language("en-US").name("X1").build());
        productService.saveI18n(b.getId(), ProductI18n.builder()
                .language("en-US").name("X2").build());

        List<ResolvedProduct> list = productService.listForDisplay("en-US", "en-US");
        assertThat(list).hasSize(2)
                .map(rp -> rp.i18n().getName()).containsExactly("X1", "X2");
    }

    @Test
    void tenantIsolation() {
        Tenant a = createTenant("prod-isol-a");
        Product pa = productService.saveProduct(newProduct("shared-slug"));
        productService.saveI18n(pa.getId(), ProductI18n.builder()
                .language("en-US").name("A's product").build());

        Tenant b = createTenant("prod-isol-b");
        Product pb = productService.saveProduct(newProduct("shared-slug"));
        productService.saveI18n(pb.getId(), ProductI18n.builder()
                .language("en-US").name("B's product").build());

        // 各租户只能看到自己的产品(按 TenantContext 隔离,tenant_id 由 DatabaseConfig 追加)
        TenantContext.set(a);
        assertThat(productService.findBySlug("shared-slug")).map(Product::getId).hasValue(pa.getId());
        TenantContext.set(b);
        // B 上下文按 slug 查到的是自己的 pb,而非 A 的 pa(越权读取隔离)
        assertThat(productService.findBySlug("shared-slug")).map(Product::getId).hasValue(pb.getId());

        // 跨租户写 i18n 视同不存在(selectById 按上下文隔离)
        TenantContext.set(a);
        assertThatThrownBy(() -> productService.saveI18n(pb.getId(), ProductI18n.builder()
                .language("zh-CN").name("hijack").build()))
                .isInstanceOf(com.rick.common.http.exception.BizException.class);
    }
}
