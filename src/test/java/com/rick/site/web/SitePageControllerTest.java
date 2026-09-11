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

    /**
     * 多语言扩展(阿/法/俄/西):新增语种经 theme.json.locales + messages.json 渲染,
     * 无需改 Java(SupportedLanguage.CODES 白名单已含)。法语渲染 LTR,阿拉伯语 RTL。
     */
    @Test
    void extendedLocalesRenderLocalized() throws Exception {
        // 法语:LTR + 法文文案
        mockMvc.perform(get("/fr-fr/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("À propos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("société de commerce")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dir=\"ltr\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"fr-FR\"")));
        // 西班牙语
        mockMvc.perform(get("/es-es/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nosotros")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("empresa comercial")));
        // 俄语
        mockMvc.perform(get("/ru-ru/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("торговая компания")));
        // 阿拉伯语:RTL + 阿语文案
        mockMvc.perform(get("/ar-sa/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dir=\"rtl\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"ar-SA\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("شركة تجارية")));
    }

    @Test
    void missingPageReturns404() throws Exception {
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }
}
