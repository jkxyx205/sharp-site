package com.rick.site.seo.web;

import com.rick.site.news.entity.Article;
import com.rick.site.news.service.ArticleService;
import com.rick.site.product.entity.Product;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-1003/1004 验收测试:/sitemap.xml 列出租户主题清单页面、上架产品、已发布新闻;
 * /robots.txt 含 Allow 与 Sitemap 引用。页面清单取自 theme.json pages(无 site_page 表)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantDomainService domainService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ArticleService articleService;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantService.save(Tenant.builder()
                .code("seo-ctrl").name("SEO Co").themeId("modern").build());
        TenantContext.set(tenant);
        domainService.add("localhost", true);

        productService.saveProduct(Product.builder()
                .slug("widget-a").status((short) 1).sort(0).build());
        articleService.saveArticle(Article.builder()
                .slug("canton-fair").author("Ed").cover("/img/n.jpg")
                .publishTime(LocalDateTime.of(2026, 5, 1, 10, 0))
                .status((short) 1).sort(0).build());
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void sitemapListsUrls() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("/about")))
                .andExpect(content().string(containsString("/products/widget-a")))
                .andExpect(content().string(containsString("/news/canton-fair")));
    }

    @Test
    void robotsTxt() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Allow: /")))
                .andExpect(content().string(containsString("Sitemap:")))
                .andExpect(content().string(containsString("/sitemap.xml")));
    }
}
