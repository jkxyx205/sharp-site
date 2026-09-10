package com.rick.site.product.service;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归:sharp-database 对 jsonb 列 UPDATE 未做 ::jsonb 转换,UPDATE product_i18n
 * 的 specification_json(setString 绑定 varchar)曾抛
 * "column is of type jsonb but expression is of type character varying"。
 *
 * <p>修复:sql/schema.sql 增加 {@code CREATE CAST (varchar AS jsonb) AS ASSIGNMENT}。
 * 本测试经框架 UPDATE 路径(同语言 i18n 二次保存)验证修复生效;依赖该 DB 转换存在。
 */
@SpringBootTest
@Transactional
class JsonbUpdateTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void updatingExistingProductI18nSpecificationJsonSucceeds() {
        Tenant t = tenantService.save(Tenant.builder()
                .code("jsonb-upd").name("JsonbUpd").themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(t);

        Product p = productService.saveProduct(Product.builder()
                .slug("jsonb-widget").status((short) 1).sort(0).build());
        // 首次保存:INSERT(原已通过)
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget").content("<p>x</p>")
                .specificationJson("{\"weight\":\"1kg\"}").build());

        // 二次保存同语言 i18n:走 UPDATE 路径,改 specification_json
        // 修复前:抛 BadSqlGrammarException(varchar 绑定到 jsonb 列)
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget").content("<p>x</p>")
                .specificationJson("{\"weight\":\"2kg\",\"color\":\"red\"}").build());

        ResolvedProduct resolved = productService.resolveForDisplay("jsonb-widget", "en-US", "en-US");
        assertThat(resolved.i18n().getSpecificationJson()).contains("2kg", "red");
    }
}
