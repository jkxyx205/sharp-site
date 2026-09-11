package com.rick.site.admin;

import com.rick.common.http.exception.BizException;
import com.rick.site.admin.entity.AdminUser;
import com.rick.site.admin.service.AdminUserService;
import com.rick.site.catalog.service.CategoryService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TASK-0903 验收测试:管理员认证 + 租户作用域 + 越权防护。
 *
 * <p>核心断言:管理员 A 在 B 的域名下访问后台,只能见本租户(A)产品,
 * 因 TenantContext 由认证主体的 tenantId 覆盖而非 Host。未登录重定向登录页;
 * 错误密码失败;密码以 BCrypt 摘要存储。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityTest {

    private static final String PWD = "s3cret-pwd";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CategoryService categoryService;

    private Tenant tenantA;
    private Tenant tenantB;
    private Long bProductId;
    private Long aCategoryId;
    private AdminUser adminA;

    @BeforeEach
    void setup() {
        tenantA = tenantService.save(Tenant.builder()
                .code("sec-a").name("Tenant A").themeId("modern").build());
        TenantContext.set(tenantA);
        domainService.add("a.example.com", true);
        adminA = adminUserService.create("a-admin", PWD);
        var aCat = categoryService.saveCategory(com.rick.site.catalog.entity.Category.builder()
                .type("PRODUCT").slug("cat-a").status((short) 1).sort(0).build());
        aCategoryId = aCat.getId();
        productService.saveProduct(Product.builder()
                .slug("widget-a").categoryId(aCategoryId).status((short) 1).sort(0).build());

        tenantB = tenantService.save(Tenant.builder()
                .code("sec-b").name("Tenant B").themeId("modern").build());
        TenantContext.set(tenantB);
        domainService.add("b.example.com", true);
        var bCat = categoryService.saveCategory(com.rick.site.catalog.entity.Category.builder()
                .type("PRODUCT").slug("cat-b").status((short) 1).sort(0).build());
        Product bProduct = productService.saveProduct(Product.builder()
                .slug("secret-b").categoryId(bCat.getId()).status((short) 1).sort(0).build());
        bProductId = bProduct.getId();
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    /** 设置 serverName(MockMvc 的 getServerName 不取 Host 头,须显式设置)。 */
    private static RequestPostProcessor host(String hostname) {
        return req -> {
            req.setServerName(hostname);
            return req;
        };
    }

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/products").with(host("a.example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://a.example.com/admin/login"));
    }

    @Test
    void badPasswordRedirectsWithError() throws Exception {
        mockMvc.perform(post("/admin/login").with(host("a.example.com")).with(csrf())
                        .param("username", "a-admin").param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void loginAndScopedAccess() throws Exception {
        // 登录 A(在 A 域名下)
        MvcResult login = mockMvc.perform(post("/admin/login").with(host("a.example.com")).with(csrf())
                        .param("username", "a-admin").param("password", PWD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // A 会话 + B 域名 → 仍只见本租户(A)产品,读不到 B 的 secret-b(作用域来自 principal 而非 Host)
        mockMvc.perform(get("/admin/products").param("categoryId", String.valueOf(aCategoryId))
                        .with(host("b.example.com")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("widget-a")))
                .andExpect(content().string(not(containsString("secret-b"))));

        // A 域名同样只见本租户
        mockMvc.perform(get("/admin/products").param("categoryId", String.valueOf(aCategoryId))
                        .with(host("a.example.com")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("widget-a")))
                .andExpect(content().string(not(containsString("secret-b"))));
    }

    @Test
    void passwordStoredAsBcryptHash() {
        assertThat(adminA.getPasswordHash()).startsWith("$2a$");
        assertThat(adminA.getPasswordHash()).isNotEqualTo(PWD);
    }

    @Test
    void crossTenantWriteRejected() {
        // 上下文为 A,直接对 B 的产品写 i18n → selectById 按 A 隔离查不到 → BizException
        TenantContext.set(tenantA);
        assertThatThrownBy(() -> productService.saveI18n(bProductId, ProductI18n.builder()
                .language("en-US").name("hijacked").content("<p>x</p>").build()))
                .isInstanceOf(BizException.class);
    }
}
