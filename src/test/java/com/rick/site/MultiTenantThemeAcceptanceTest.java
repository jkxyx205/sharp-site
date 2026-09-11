package com.rick.site;

import com.rick.site.product.entity.Product;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import com.rick.site.theme.service.ThemeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-1501 / TASK-1502 最终验收:多租户 + 主题映射。
 *
 * <p>按 TASKS.md 建立三个租户:
 * <pre>
 *   tenant 100 → modern
 *   tenant 200 → modern
 *   tenant 300 → industrial
 * </pre>
 * 验证:
 * <ul>
 *   <li>{@link ThemeResolver} 按各租户 themeId 解析(不按 tenantId 分支,§3)</li>
 *   <li>多租户数据隔离:100 与 200 同 slug 产品互不可见(上下文驱动 tenant_id 过滤)</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class MultiTenantThemeAcceptanceTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private ThemeResolver themeResolver;
    @Autowired
    private ProductService productService;

    private Tenant t100;
    private Tenant t200;
    private Tenant t300;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant create(String code, String themeId) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId(themeId).build());
        TenantContext.set(t);
        return t;
    }

    private Product seedProduct(String slug) {
        return productService.saveProduct(Product.builder()
                .slug(slug).status((short) 1).sort(0).build());
    }

    @Test
    void themesResolvePerTenantAndDataIsolatesAcrossTenants() {
        // TASK-1502:主题映射 100→modern, 200→modern, 300→industrial
        t100 = create("100", "modern");
        Product p100 = seedProduct("shared-widget");

        t200 = create("200", "modern");
        Product p200 = seedProduct("shared-widget");

        t300 = create("300", "industrial");

        assertThat(themeResolver.resolveTheme(t100)).isEqualTo("modern");
        assertThat(themeResolver.resolveTheme(t200)).isEqualTo("modern");
        assertThat(themeResolver.resolveTheme(t300)).isEqualTo("industrial");

        // TASK-1501:多租户数据隔离
        // 100 上下文:findBySlug 命中 100 自己的产品(因 tenant_id 过滤,看不到 200)
        TenantContext.set(t100);
        assertThat(productService.findBySlug("shared-widget"))
                .map(Product::getId).hasValue(p100.getId());
        assertThat(productService.listByTenant())
                .map(Product::getId).containsExactly(p100.getId());

        // 200 上下文:同 slug 命中 200 自己的产品,与 100 不交叉
        TenantContext.set(t200);
        assertThat(productService.findBySlug("shared-widget"))
                .map(Product::getId).hasValue(p200.getId());
        assertThat(productService.listByTenant())
                .map(Product::getId).containsExactly(p200.getId())
                .doesNotContain(p100.getId());
    }
}
