package com.rick.site.web;

import com.rick.site.product.entity.Product;
import com.rick.site.product.entity.ProductI18n;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-0604 验收测试:产品列表 + 详情前台渲染(含 zh-cn locale 前缀)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    @Autowired
    private ProductService productService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("prod-ctrl").name("Prod Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        Product a = productService.saveProduct(Product.builder()
                .slug("widget-a").cover("/img/a.jpg").status((short) 1).sort(0).build());
        productService.saveI18n(a.getId(), ProductI18n.builder()
                .language("en-US").name("Widget A").subtitle("Pro")
                .content("<p>Detail of A</p>").seoTitle("Widget A SEO").build());
        productService.saveI18n(a.getId(), ProductI18n.builder()
                .language("zh-CN").name("小部件A").content("<p>A 详情</p>").build());

        productService.saveProduct(Product.builder()
                .slug("widget-b").cover("/img/b.jpg").status((short) 1).sort(1).build());
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void listRendersProducts() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Widget A")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/products/widget-a")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<style")));
    }

    @Test
    void detailRendersProduct() throws Exception {
        mockMvc.perform(get("/products/widget-a"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Widget A")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Detail of A")));
    }

    @Test
    void localePrefixRendersLocalized() throws Exception {
        mockMvc.perform(get("/zh-cn/products/widget-a"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("小部件A")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A 详情")));
    }

    @Test
    void missingProductReturns404() throws Exception {
        mockMvc.perform(get("/products/nope"))
                .andExpect(status().isNotFound());
    }
}
