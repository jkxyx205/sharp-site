package com.rick.site.admin;

import com.rick.common.http.exception.BizException;
import com.rick.site.admin.service.AdminUserService;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.news.service.ArticleService;
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
    @Autowired private CategoryService categoryService;
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
        // Phase 19 单语种表单:每次保存仅写该语种一行;分两次保存 en-US / zh-CN
        MvcResult r = mockMvc.perform(post("/admin/products/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "widget-crud").param("status", "1").param("sort", "0")
                        .param("language", "en-US").param("name", "Widget CRUD").param("content", "<p>en</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/products/*/edit?lang=en-US"))
                .andReturn();
        Long id = Long.valueOf(r.getResponse().getRedirectedUrl()
                .replaceAll(".*/(\\d+)/edit.*", "$1"));

        mockMvc.perform(post("/admin/products/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("id", String.valueOf(id))
                        .param("slug", "widget-crud").param("status", "1").param("sort", "0")
                        .param("language", "zh-CN").param("name", "小工具").param("content", "<p>中</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/products/*/edit?lang=zh-CN"));

        TenantContext.set(tenantA);
        var product = productService.findBySlug("widget-crud").orElseThrow();
        Map<String, ProductI18n> map = productService.loadI18nMap(product.getId());
        assertThat(map).containsKeys("en-US", "zh-CN");
        assertThat(map.get("en-US").getName()).isEqualTo("Widget CRUD");
        assertThat(map.get("zh-CN").getName()).isEqualTo("小工具");
        assertThat(product.getTenantId()).isEqualTo(tenantA.getId());
        TenantContext.clear();
    }

    /**
     * 回显验收(Request F):编辑产品时,按当前语种把 product_i18n 回显到表单。
     */
    @Test
    void getEditFormEchoesSavedProductI18n() throws Exception {
        MvcResult r = mockMvc.perform(post("/admin/products/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "echo-prod").param("status", "1").param("sort", "0")
                        .param("language", "zh-CN").param("name", "小工具").param("content", "<p>中</p>"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Long id = Long.valueOf(r.getResponse().getRedirectedUrl().replaceAll(".*/(\\d+)/edit.*", "$1"));

        String zhHtml = mockMvc.perform(get("/admin/products/" + id + "/edit").param("lang", "zh-CN")
                        .with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(zhHtml).contains("小工具");
    }

    @Test
    void saveArticleWritesBothLanguages() throws Exception {
        // 文章(单语种表单):分两次保存 en-US / zh-CN
        MvcResult r = mockMvc.perform(post("/admin/news/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "launch-crud").param("status", "1").param("sort", "0")
                        .param("language", "en-US").param("title", "Launched").param("content", "<p>en</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/news/*/edit?lang=en-US"))
                .andReturn();
        Long articleId = Long.valueOf(r.getResponse().getRedirectedUrl()
                .replaceAll(".*/(\\d+)/edit.*", "$1"));

        mockMvc.perform(post("/admin/news/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("id", String.valueOf(articleId))
                        .param("slug", "launch-crud").param("status", "1").param("sort", "0")
                        .param("language", "zh-CN").param("title", "上线").param("content", "<p>中</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/news/*/edit?lang=zh-CN"));

        TenantContext.set(tenantA);
        var article = articleService.findBySlug("launch-crud").orElseThrow();
        assertThat(articleService.loadI18nMap(article.getId())).containsKeys("en-US", "zh-CN");
        TenantContext.clear();
    }

    /**
     * 回显验收(Request F):编辑新闻时,按当前语种查询 article_i18n 并把内容回显到表单。
     * 复现用户报告"编辑内容,多语言区域的内容是空,没有回显"。
     */
    @Test
    void getEditFormEchoesSavedI18nContent() throws Exception {
        // 1) 保存 zh-CN + en-US 两行 i18n
        MvcResult r = mockMvc.perform(post("/admin/news/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("slug", "echo-crud").param("status", "1").param("sort", "0")
                        .param("language", "en-US").param("title", "Launched").param("content", "<p>en</p>"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long articleId = Long.valueOf(r.getResponse().getRedirectedUrl()
                .replaceAll(".*/(\\d+)/edit.*", "$1"));
        mockMvc.perform(post("/admin/news/save").with(host("crud-a.example.com")).session(sessionA).with(csrf())
                        .param("id", String.valueOf(articleId))
                        .param("slug", "echo-crud").param("status", "1").param("sort", "0")
                        .param("language", "zh-CN").param("title", "上线").param("content", "<p>中</p>"))
                .andExpect(status().is3xxRedirection());

        // 2) GET 编辑表单 ?lang=zh-CN → zh-CN 内容应回显到 input/textarea
        String zhHtml = mockMvc.perform(get("/admin/news/" + articleId + "/edit")
                        .param("lang", "zh-CN")
                        .with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(zhHtml).contains("上线");

        // 3) GET 编辑表单 ?lang=en-US → en-US 内容应回显
        String enHtml = mockMvc.perform(get("/admin/news/" + articleId + "/edit")
                        .param("lang", "en-US")
                        .with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(enHtml).contains("Launched");
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
        // A 与 B 各建一个 PRODUCT 分类 + 一个归属该分类的产品
        TenantContext.set(tenantA);
        var aCat = categoryService.saveCategory(com.rick.site.catalog.entity.Category.builder()
                .type("PRODUCT").slug("cat-a").status((short) 1).sort(0).build());
        productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("a-only").categoryId(aCat.getId()).status((short) 1).sort(0).build());
        Tenant tenantB = tenantService.save(Tenant.builder()
                .code("crud-b2").name("CRUD B2").themeId("modern").build());
        TenantContext.set(tenantB);
        var bCat = categoryService.saveCategory(com.rick.site.catalog.entity.Category.builder()
                .type("PRODUCT").slug("cat-b").status((short) 1).sort(0).build());
        productService.saveProduct(com.rick.site.product.entity.Product.builder()
                .slug("b-only").categoryId(bCat.getId()).status((short) 1).sort(0).build());
        TenantContext.clear();

        // A 会话按 A 的分类筛选,只见 a-only,不见 b-only
        mockMvc.perform(get("/admin/products").param("categoryId", String.valueOf(aCat.getId()))
                        .with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a-only")))
                .andExpect(content().string(not(containsString("b-only"))));

        // 未选分类 → 显示全部分类下的产品(仍仅本租户)
        mockMvc.perform(get("/admin/products").with(host("crud-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a-only")))
                .andExpect(content().string(not(containsString("b-only"))));
    }
}
