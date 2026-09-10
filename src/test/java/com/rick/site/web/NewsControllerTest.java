package com.rick.site.web;

import com.rick.site.news.entity.Article;
import com.rick.site.news.entity.ArticleI18n;
import com.rick.site.news.service.ArticleService;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-0704 验收测试:新闻列表 + 详情前台渲染(含 locale 前缀)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    @Autowired
    private ArticleService articleService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("news-ctrl").name("News Co").themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        Article a = articleService.saveArticle(Article.builder()
                .slug("canton-fair").author("Editor").cover("/img/news.jpg")
                .publishTime(LocalDateTime.of(2026, 5, 1, 10, 0))
                .status((short) 1).sort(0).build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("en-US").title("We exhibited at Canton Fair")
                .summary("A great show").content("<p>News body</p>")
                .seoTitle("Canton Fair SEO").build());
        articleService.saveI18n(a.getId(), ArticleI18n.builder()
                .language("zh-CN").title("广交会参展").content("<p>正文</p>").build());

        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void listRendersNews() throws Exception {
        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Canton Fair")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/news/canton-fair")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/themes/modern/css/style.css")));
    }

    @Test
    void detailRendersArticle() throws Exception {
        mockMvc.perform(get("/news/canton-fair"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Canton Fair SEO")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("News body")));
    }

    @Test
    void localePrefixRendersLocalized() throws Exception {
        mockMvc.perform(get("/zh-cn/news/canton-fair"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("广交会")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("正文")));
    }

    @Test
    void missingArticleReturns404() throws Exception {
        mockMvc.perform(get("/news/nope"))
                .andExpect(status().isNotFound());
    }
}
