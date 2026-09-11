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
 * TASK-0502 验收测试:首页由模板 + messages.json 文案键渲染(无 home_section 表)。
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

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("home-ctrl").name("Home Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void homeRendersFromMessages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome to Our Site")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Premium products for global trade")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We supply quality goods")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ready to work with us?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<style")));
    }
}
