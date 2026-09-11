package com.rick.site.admin;

import com.rick.site.admin.service.AdminUserService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TASK-1401 验收测试:后台域名管理 UI(/admin/domains)需认证、按租户作用域,
 * 支持 添加 / 设为主域名 / 删除,PRG 重定向,GET 列出当前租户域名。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDomainUITest {

    private static final String PWD = "dom-pwd";

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
                .code("domui").name("DomUI Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);
        adminUserService.create("domui-admin", PWD);
        TenantContext.clear();

        MvcResult login = mockMvc.perform(post("/admin/login").with(host("localhost")).with(csrf())
                        .param("username", "domui-admin").param("password", PWD))
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
        mockMvc.perform(get("/admin/domains").with(host("localhost")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void getDomainsViewShowsFormAndList() throws Exception {
        mockMvc.perform(get("/admin/domains").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("域名管理")))
                .andExpect(content().string(containsString("localhost")))
                .andExpect(content().string(containsString("添加域名")));
    }

    @Test
    void addPrimaryDomainShowsInList() throws Exception {
        mockMvc.perform(post("/admin/domains").with(host("localhost")).session(session).with(csrf())
                        .param("domain", "www.example.com").param("primary", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains"));

        mockMvc.perform(get("/admin/domains").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("www.example.com")));
    }

    @Test
    void addInvalidDomainShowsError() throws Exception {
        mockMvc.perform(post("/admin/domains").with(host("localhost")).session(session).with(csrf())
                        .param("domain", "not a valid domain").param("primary", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains"));

        mockMvc.perform(get("/admin/domains").with(host("localhost")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("非法域名格式")));
    }
}
