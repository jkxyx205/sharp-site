package com.rick.site.web;

import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.page.service.SitePageService;
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
 * <p>TenantFilter 从 Host=localhost 解析租户,LocaleFilter 解析语言;
 * 默认语言 {@code /about} 与 {@code /zh-cn/about} 均渲染 modern 主题模板。
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

    @Autowired
    private SitePageService pageService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("web-ctrl").name("Web Ctrl").themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        SitePage about = pageService.savePage(SitePage.builder()
                .pageKey("about").path("/about").template("themes/modern/about").status((short) 1).build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("en-US").title("About Us").content("<p>We are great</p>").build());
        pageService.saveI18n(about.getId(), SitePageI18n.builder()
                .language("zh-CN").title("关于我们").content("<p>我们很棒</p>").build());

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We are great")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/themes/modern/css/style.css")));
    }

    @Test
    void localePrefixRendersLocalized() throws Exception {
        mockMvc.perform(get("/zh-cn/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("关于我们")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("我们很棒")));
    }

    @Test
    void missingPageReturns404() throws Exception {
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }
}
