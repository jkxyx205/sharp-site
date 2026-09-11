package com.rick.site.admin;

import com.rick.site.admin.service.AdminUserService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TASK-1305 验收测试:后台发布 UI(/admin/publish)需认证、按租户作用域、
 * GET 列出发布记录、POST 触发发布并 PRG 重定向。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPublishUITest {

    private static final String PWD = "pub-pwd";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void wwwRoot(DynamicPropertyRegistry reg) {
        reg.add("sharp.site.www-root", () -> tempDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private AdminUserService adminUserService;

    private MockHttpSession session;

    @BeforeEach
    void setup() throws Exception {
        Tenant tenant = tenantService.save(Tenant.builder()
                .code("pubui").name("PubUI Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        adminUserService.create("pubui-admin", PWD);
        TenantContext.clear();

        MvcResult login = mockMvc.perform(post("/admin/login").with(host("localhost")).with(csrf())
                        .param("username", "pubui-admin").param("password", PWD))
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
        mockMvc.perform(get("/admin/publish").with(host("localhost")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void getPublishViewShowsFormAndEmptyState() throws Exception {
        mockMvc.perform(get("/admin/publish").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("网站发布")))
                .andExpect(content().string(containsString("暂无发布记录")));
    }

    @Test
    void postPublishTriggersAndShowsRecord() throws Exception {
        // POST 发布 → 重定向回 /admin/publish(PRG)
        mockMvc.perform(post("/admin/publish").with(host("localhost")).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/publish"));

        // GET 列表应出现 v001 / SUCCESS
        mockMvc.perform(get("/admin/publish").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("v001")))
                .andExpect(content().string(containsString("SUCCESS")));
    }
}
