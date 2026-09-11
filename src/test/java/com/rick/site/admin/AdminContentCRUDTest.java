package com.rick.site.admin;

import com.rick.common.http.exception.BizException;
import com.rick.site.admin.service.AdminUserService;
import com.rick.site.news.service.ArticleService;
import com.rick.site.page.service.SitePageService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 17 验收:后台内容 CRUD + 多语言 i18n 编辑 + 租户作用域/越权防护。
 *
 * <p>核心断言:经后台 POST 保存的内容写入父表 + 双语言 i18n 行(zh-CN + en-US);
 * 跨租户 id 经 requireOwned 隔离查不到 → BizException;删除(逻辑)后 selectById 为空;
 * 列表仅本租户可见。复用 AdminSecurityTest 的 host()+csrf()+session 登录模式。
 *
 * @author Rick.Xu
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminContentCRUDTest {

    private static final String PWD = "crud-pwd";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantService tenantService;
    @Autowired private TenantDomainService domainService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private ProductService productService;
    @Autowired private SitePageService pageService;
    @Autowired private ArticleService articleService;

    private Tenant tenantA;
    private MockHttpSession sessionA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenantService.save(Tenant.builder()
                .code("crud-a").name("CRUD A").themeId("modern").build());
        TenantContext.set(tenantA);
        domainService.add("crud-a.example.com", true);
        adminUserService.create("crud-admin", PWD);
        TenantContext.clear();

        MvcResult login = mockMvc.perform(post("/admin/login").with(host("crud-a.example.com")).with(csrf())
                        .param("username", "crud-admin").param("password", PWD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"))
                .andReturn();
        sessionA = (MockHttpSession) login.getRequest().getSession(false);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    /** MockMvc 的 getServerName 不取 Host 头,须显式设置。 */
    private static RequestPostProcessor host(String hostname) {
        return req -> { req.setServerName(hostname); return req; };
    }

    @Test
    void saveProductWritesBothLanguages() throws Exception {
        mockMvc.perform(post("/admin/products/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "widget-crud").param("status", "1").param("sort", "0")
                        .param("name_en-US", "Widget CRUD").param("content_en-US", "<p>en</p>")
                        .param("name_zh-CN", "小工具").param("content_zh-CN", "<p>中</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        TenantContext.set(tenantA);
        var product = productService.findBySlug("widget-crud").orElseThrow();
        Map<String, ProductI18n> map = productService.loadI18nMap(product.getId());
        assertThat(map).containsKeys("en-US", "zh-CN");
        assertThat(map.get("en-US").getName()).isEqualTo("Widget CRUD");
        assertThat(map.get("zh-CN").getName()).isEqualTo("小工具");
        assertThat(product.getTenantId()).isEqualTo(tenantA.getId());
        TenantContext.clear();
    }

    @Test
    void savePageAndArticleWriteBothLanguages() throws Exception {
        // 页面:title+content+cover 三项全空则跳过该语言;这里两种语言都填
        mockMvc.perform(post("/admin/pages/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("pageKey", "services").param("path", "/services")
                        .param("template", "themes/modern/about").param("status", "1")
                        .param("title_en-US", "Services").param("content_en-US", "<p>en</p>")
                        .param("title_zh-CN", "服务").param("content_zh-CN", "<p>中</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pages"));

        mockMvc.perform(post("/admin/news/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "launch-crud").param("status", "1").param("sort", "0")
                        .param("title_en-US", "Launched").param("content_en-US", "<p>en</p>")
                        .param("title_zh-CN", "上线").param("content_zh-CN", "<p>中</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/news"));

        TenantContext.set(tenantA);
        var page = pageService.findByKey("services").orElseThrow();
        assertThat(pageService.loadI18nMap(page.getId())).containsKeys("en-US", "zh-CN");
        var article = articleService.findBySlug("launch-crud").orElseThrow();
        assertThat(articleService.loadI18nMap(article.getId())).containsKeys("en-US", "zh-CN");
        TenantContext.clear();
    }

    @Test
    void crossTenantWriteRejected() {
        // 租户 B 的产品
        Tenant tenantB = tenantService.save(Tenant.builder()
                .code("crud-b").name("CRUD B").themeId("modern").build());
        TenantContext.set(tenantB);
        var bProduct = productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("secret-b").status((short) 1).sort(0).build());
        Long bId = bProduct.getId();

        // A 上下文对 B 的产品写 i18n → requireOwned 按 A 隔离查不到 → BizException
        TenantContext.set(tenantA);
        assertThatThrownBy(() -> productService.saveI18n(bId, ProductI18n.builder()
                        .language("en-US").name("hijacked").content("<p>x</p>").build()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void deleteSoftDeletesOwnProduct() throws Exception {
        // A 创建产品
        TenantContext.set(tenantA);
        var p = productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("to-delete").status((short) 1).sort(0).build());
        Long id = p.getId();
        TenantContext.clear();

        mockMvc.perform(post("/admin/products/" + id + "/delete")
                        .with(host("crud-a.example.com")).session(sessionA).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        TenantContext.set(tenantA);
        assertThat(productService.selectById(id)).isEmpty();
        TenantContext.clear();
    }

    @Test
    void listScopedToOwnTenant() throws Exception {
        // A 与 B 各一产品
        TenantContext.set(tenantA);
        productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("a-only").status((short) 1).sort(0).build());
        Tenant tenantB = tenantService.save(Tenant.builder()
                .code("crud-b2").name("CRUD B2").themeId("modern").build());
        TenantContext.set(tenantB);
        productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("b-only").status((short) 1).sort(0).build());
        TenantContext.clear();

        // A 会话列表只见 a-only,不见 b-only
        mockMvc.perform(get("/admin/products").with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a-only")))
                .andExpect(content().string(not(containsString("b-only"))));
    }
}
