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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 后台素材管理验收:上传 → 列表回显(图片预览 + 可复制 URL)→ 删除;租户隔离。
 *
 * <p>复用 AdminSecurityTest 的 host()+csrf()+session 登录模式。上传经 sharp-fileupload,
 * 目录按租户 code 划分;列表仅本租户可见。图片在列表内直接预览。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMediaUITest {

    private static final String PWD = "media-pwd";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantService tenantService;
    @Autowired private TenantDomainService domainService;
    @Autowired private AdminUserService adminUserService;

    private Tenant tenantA;
    private MockHttpSession sessionA;

    @BeforeEach
    void setup() throws Exception {
        tenantA = tenantService.save(Tenant.builder()
                .code("media-ui-a").name("Media UI A").themeId("modern").build());
        TenantContext.set(tenantA);
        domainService.add("media-ui-a.example.com", true);
        adminUserService.create("m-admin", PWD);
        TenantContext.clear();

        MvcResult login = mockMvc.perform(post("/admin/login").with(host("media-ui-a.example.com")).with(csrf())
                        .param("username", "m-admin").param("password", PWD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"))
                .andReturn();
        sessionA = (MockHttpSession) login.getRequest().getSession(false);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private static RequestPostProcessor host(String hostname) {
        return req -> { req.setServerName(hostname); return req; };
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png",
                "png-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void uploadThenListShowsPreviewAndCopyableUrl() throws Exception {
        // 上传一张图片
        mockMvc.perform(multipart("/admin/media/upload").file(png("hero.png"))
                        .param("title", "Hero").param("altText", "hero alt")
                        .with(host("media-ui-a.example.com")).session(sessionA).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/media"));

        // 列表回显:含图片预览 <img> + 可复制 URL + 文件名
        String html = mockMvc.perform(get("/admin/media")
                        .with(host("media-ui-a.example.com")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero.png")))
                .andExpect(content().string(containsString("image/png")))
                // 图片预览:<img ... src="...hero 路径...">;URL 输入框可复制
                .andExpect(content().string(containsString("<img")))
                .andExpect(content().string(containsString("id=\"url-")))
                .andExpect(content().string(containsString("复制")))
                .andReturn().getResponse().getContentAsString();

        // 目录按租户 code 划分:URL/路径含 /media-ui-a/
        assertThat(html).contains("/media-ui-a/");
    }

    @Test
    void deleteRemovesFromList() throws Exception {
        MvcResult up = mockMvc.perform(multipart("/admin/media/upload").file(png("del.png"))
                        .with(host("media-ui-a.example.com")).session(sessionA).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        // 列表出现
        mockMvc.perform(get("/admin/media").with(host("media-ui-a.example.com")).session(sessionA))
                .andExpect(content().string(containsString("del.png")));

        // 取 id 后删除
        String html = mockMvc.perform(get("/admin/media").with(host("media-ui-a.example.com")).session(sessionA))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(html.replaceAll("(?s).*?/admin/media/(\\d+)/delete.*", "$1"));

        mockMvc.perform(post("/admin/media/" + id + "/delete")
                        .with(host("media-ui-a.example.com")).session(sessionA).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/media"));

        mockMvc.perform(get("/admin/media").with(host("media-ui-a.example.com")).session(sessionA))
                .andExpect(content().string(not(containsString("del.png"))));
    }
}
