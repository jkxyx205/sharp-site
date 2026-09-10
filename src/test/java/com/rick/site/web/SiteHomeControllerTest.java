package com.rick.site.web;

import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService;
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
 * TASK-0502 验收测试:首页由区块数据驱动渲染。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SiteHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    @Autowired
    private HomeSectionService homeSectionService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("home-ctrl").name("Home Co").themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        HomeSection hero = homeSectionService.saveSection(
                HomeSection.builder().sectionKey("hero").sort(0).enabled((short) 1).build());
        homeSectionService.saveI18n(hero.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Welcome Home").subtitle("We build great things").build());

        HomeSection company = homeSectionService.saveSection(
                HomeSection.builder().sectionKey("company").sort(1).enabled((short) 1).build());
        homeSectionService.saveI18n(company.getId(), HomeSectionI18n.builder()
                .language("en-US").title("About Us").content("<p>Founded in 2010</p>").build());

        HomeSection cta = homeSectionService.saveSection(
                HomeSection.builder().sectionKey("cta").sort(2).enabled((short) 1).build());
        homeSectionService.saveI18n(cta.getId(), HomeSectionI18n.builder()
                .language("en-US").title("Ready to work with us?").build());

        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void homeRendersSections() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome Home")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We build great things")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Founded in 2010")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ready to work with us?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<style")));
    }
}
