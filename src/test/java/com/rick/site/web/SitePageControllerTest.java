package com.rick.site.web;

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
 * TASK-0402 验收测试:页面前台路由 + i18n + 主题渲染。
 *
 * <p>页面(about/contact)由前端模板维护,清单见 theme.json 的 pages;正文文案由
 * {@code messages.json} 文案键提供(无 site_page 表)。TenantFilter 从 Host=localhost
 * 解析租户,LocaleFilter 解析语言;默认语言 {@code /about} 与 {@code /zh-cn/about}
 * 均渲染 modern 主题模板。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SitePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("web-ctrl").name("Web Ctrl").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void defaultLanguageRendersPage() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("About Us")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("trading company")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<style")));
    }

    @Test
    void localePrefixRendersLocalized() throws Exception {
        mockMvc.perform(get("/zh-cn/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("关于我们")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("贸易公司")));
    }

    @Test
    void missingPageReturns404() throws Exception {
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }
}
