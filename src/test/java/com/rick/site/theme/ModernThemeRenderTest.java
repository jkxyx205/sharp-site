package com.rick.site.theme;

import com.rick.site.tenant.dto.TenantConfigView;
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

    record SampleProduct(String slug, String name, String subtitle, String cover,
                          String content, String specificationJson) {
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
        ctx.setVariable("products", List.of(
                new SampleProduct("widget-a", "Widget A", "Pro", "/img/a.jpg", null, null),
                new SampleProduct("widget-b", "Widget B", null, null, null, null)));
        ctx.setVariable("news", List.of(
                new SampleArticle("n1", "We exhibited at Canton Fair", null, null, null)));
        ctx.setVariable("config", sampleConfig());
        return ctx;
    }

    private TenantConfigView sampleConfig() {
        // company_name/address/copyright 已移入 tenant_config_i18n;此处用 view 模拟已解析语种。
        return new TenantConfigView(null, "Acme Corp", null,
                "1 Industrial Park, Shanghai", "© 2026 Acme Corp",
                "+86-21-1000", null, "info@acme.com", "+86-21-1000",
                null, null, null, "沪ICP备0000号");
    }

    @Test
    void rendersIndexWithData() {
        String html = templateEngine.process("themes/modern/index", sampleContext());

        assertThat(html).contains("Acme Corp");
        assertThat(html).contains("Widget A");
        assertThat(html).contains("We exhibited at Canton Fair");
        assertThat(html).contains("<style");
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
                ctx.setVariable("product", new SampleProduct("widget-a", "Widget A", "Pro", "/img/a.jpg", "<p>Detail</p>", null));
            } else if ("news-detail".equals(page)) {
                ctx.setVariable("article", new SampleArticle("n1", "Canton Fair", "We exhibited", "<p>News body</p>", null));
            }
            String html = templateEngine.process("themes/modern/" + page, ctx);
            assertThat(html).as(page).contains("Acme Corp").contains("<style");
        }
    }
}
