package com.rick.site.theme;

import com.rick.site.tenant.entity.TenantConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0202 / TASK-0203 验收测试:modern 主题模板可被 Thymeleaf 渲染,
 * 数据来自模型(非硬编码),CSS 链接就位且含响应式断点。
 *
 * <p>用 WebContext(非纯 Context)以支持 @{...} 链接表达式;
 * 产品/新闻用带访问器的 record(模拟未来实体 getter),避免 Map 的 SpEL 限制。
 */
@SpringBootTest
class ModernThemeRenderTest {

    @Autowired
    private TemplateEngine templateEngine;

    record SampleProduct(String slug, String name, String subtitle, String cover, String content) {
    }

    record SampleArticle(String slug, String title, String summary, String content, LocalDateTime publishTime) {
    }

    private WebContext sampleContext() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setContextPath("");
        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext ctx = new WebContext(app.buildExchange(req, new MockHttpServletResponse()), req.getLocale(), null);
        ctx.setVariable("siteName", "Acme Corp");
        ctx.setVariable("pageTitle", "Home");
        ctx.setVariable("pageDescription", "Acme foreign trade");
        ctx.setVariable("pageKeywords", "export,manufacturing");
        ctx.setVariable("canonical", "https://acme.example.com/");
        ctx.setVariable("robots", "index, follow");
        ctx.setVariable("tagline", "Quality You Can Trust");
        ctx.setVariable("intro", "We make great things.");
        ctx.setVariable("aboutTeaser", "Founded in 2010.");
        ctx.setVariable("ctaTitle", "Let's talk");
        ctx.setVariable("products", List.of(
                new SampleProduct("widget-a", "Widget A", "Pro", "/img/a.jpg", null),
                new SampleProduct("widget-b", "Widget B", null, null, null)));
        ctx.setVariable("news", List.of(
                new SampleArticle("n1", "We exhibited at Canton Fair", null, null, null)));
        ctx.setVariable("config", sampleConfig());
        return ctx;
    }

    private TenantConfig sampleConfig() {
        return TenantConfig.builder()
                .companyName("Acme Corp")
                .address("1 Industrial Park, Shanghai")
                .email("info@acme.com")
                .phone("+86-21-1000")
                .whatsapp("+86-21-1000")
                .copyright("© 2026 Acme Corp")
                .icp("沪ICP备0000号")
                .build();
    }

    @Test
    void rendersIndexWithData() {
        String html = templateEngine.process("themes/modern/index", sampleContext());

        assertThat(html).contains("Acme Corp");
        assertThat(html).contains("Widget A");
        assertThat(html).contains("We exhibited at Canton Fair");
        assertThat(html).contains("/themes/modern/css/style.css");
        assertThat(html).contains("info@acme.com");
        assertThat(html).contains("沪ICP备0000号");
    }

    @Test
    void rendersContactPage() {
        WebContext ctx = sampleContext();
        ctx.setVariable("pageTitle", "Contact");
        String html = templateEngine.process("themes/modern/contact", ctx);

        assertThat(html).contains("Acme Corp");
        assertThat(html).contains("info@acme.com");
        assertThat(html).contains("contact-form");
    }

    @Test
    void rendersAllPagesWithoutError() {
        for (String page : List.of("index", "about", "products", "product-detail",
                "news", "news-detail", "contact")) {
            WebContext ctx = sampleContext();
            if ("product-detail".equals(page)) {
                ctx.setVariable("product", new SampleProduct("widget-a", "Widget A", "Pro", "/img/a.jpg", "<p>Detail</p>"));
            } else if ("news-detail".equals(page)) {
                ctx.setVariable("article", new SampleArticle("n1", "Canton Fair", "We exhibited", "<p>News body</p>", null));
            }
            String html = templateEngine.process("themes/modern/" + page, ctx);
            assertThat(html).as(page).contains("Acme Corp").contains("/themes/modern/css/style.css");
        }
    }
}
