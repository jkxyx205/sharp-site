package com.rick.site.preview.web;

import com.rick.site.admin.service.AdminUserService;
import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService;
import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.page.service.SitePageService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TASK-1101 验收测试:预览需认证、按认证管理员租户作用域、多语言 ?lang、noindex。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PreviewControllerTest {

    private static final String PWD = "preview-pwd";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private HomeSectionService homeSectionService;
    @Autowired
    private SitePageService pageService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ArticleService articleService;

    private MockHttpSession session;

    @BeforeEach
    void setup() throws Exception {
        Tenant tenant = tenantService.save(Tenant.builder()
                .code("pvw").name("Preview Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        HomeSection hero = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("hero").sort(0).enabled((short) 1).build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome Home").subtitle("We build great things").build());
        HomeSection company = homeSectionService.saveSection(HomeSection.builder()
                .sectionKey("company").sort(1).enabled((short) 1).build());
        homeSectionService.saveI18n(company.getId(), HomeSectionI18n.builder()
                .language("en-US").title("About Us").content("<p>Founded in 2010</p>").build());

        SitePage about = pageService.savePage(SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about").status((short) 1).build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("en-US").title("About Us").content("<p>We are great</p>").build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("zh-CN").title("关于我们").content("<p>我们很棒</p>").build());
        Product p = productService.saveProduct(Product.builder()
                .slug("widget-a").status((short) 1).sort(0).build());
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("en-US").name("Widget A").content("<p>Detail A</p>").build());
        productService.saveI18n(p.getId(), ProductI18n.builder()
                .language("zh-CN").name("小部件A").content("<p>A 详情</p>").build());
        Article a = articleService.saveArticle(Article.builder()
                .slug("canton-fair").author("Ed").status((short) 1).sort(0).build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("Canton Fair").content("<p>News body</p>").build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("zh-CN").title("广交会").content("<p>正文</p>").build());
        adminUserService.create("pvw-admin", PWD);
        TenantContext.clear();

        // 登录拿会话(在租户域名下)
        MvcResult login = mockMvc.perform(post("/admin/login").with(host("localhost")).with(csrf())
                        .param("username", "pvw-admin").param("password", PWD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"))
                .andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private static RequestPostProcessor host(String hostname) {
        return req -> {
            req.setServerName(hostname);
            return req;
        };
    }

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/preview/").with(host("localhost")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void authenticatedHomePreviewRenders() throws Exception {
        mockMvc.perform(get("/preview/").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome Home")))
                .andExpect(content().string(containsString("noindex, nofollow")));
    }

    @Test
    void productPreviewMultiLanguage() throws Exception {
        mockMvc.perform(get("/preview/products/widget-a").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Widget A")))
                .andExpect(content().string(containsString("Detail A")));
        // ?lang=zh-CN 渲染中文 draft
        mockMvc.perform(get("/preview/products/widget-a").with(host("localhost")).session(session)
                        .param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("小部件A")))
                .andExpect(content().string(containsString("A 详情")));
    }

    @Test
    void pagePreviewRenders() throws Exception {
        mockMvc.perform(get("/preview/about").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("About Us")))
                .andExpect(content().string(containsString("We are great")));
    }

    @Test
    void newsPreviewRenders() throws Exception {
        mockMvc.perform(get("/preview/news/canton-fair").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canton Fair")));
    }

    @Test
    void previewScopedToAdminTenantRegardlessOfHost() throws Exception {
        // 即便 Host 指向其它域名,预览仍读认证管理员所属租户(A)数据(作用域来自 principal)
        mockMvc.perform(get("/preview/products/widget-a").with(host("other.example.com")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Widget A")))
                .andExpect(content().string(not(containsString("secret-b"))));
    }
}
