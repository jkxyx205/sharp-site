package com.rick.site.theme;

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
 * 回归:info.html 通用页面按 categorySlug 过滤渲染 news / products 板块。
 *
 * <p>info.html 由 {@code themes/modern/meta/theme.json} 注册(path=/info),
 * 在线上(SitePageController)、预览(PreviewController.page)、静态(StaticSiteGenerator.generatePages)
 * 三路均注入全量 products/news 列表后渲染。本测试直接喂入带 categorySlug 的列表,
 * 确保 c-news 新闻板块与 test 产品板块渲染正确、不抛异常。
 */
@SpringBootTest
class InfoPageRenderTest {

    @Autowired
    private TemplateEngine templateEngine;

    record SampleProduct(String slug, String cover, String name, String subtitle,
                         String categorySlug, String categoryName) {
    }

    record SampleArticle(String slug, String title, String summary, String content,
                         LocalDateTime publishTime, String categorySlug, String categoryName) {
    }

    private WebContext ctx() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/info");
        req.setContextPath("");
        MockServletContext sc = new MockServletContext();
        JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(sc);
        WebContext ctx = new WebContext(app.buildExchange(req, new MockHttpServletResponse()), req.getLocale(), null);
        ctx.setVariable("siteName", "Acme");
        ctx.setVariable("pageTitle", "Info");
        ctx.setVariable("pageDescription", "");
        ctx.setVariable("pageKeywords", "");
        ctx.setVariable("canonical", "https://acme.example.com/info");
        ctx.setVariable("robots", "index, follow");
        ctx.setVariable("localePrefix", "");
        // 全量列表(线上/预览/静态均注入完整列表;模板内按 categorySlug 过滤)
        ctx.setVariable("products", List.of(
                new SampleProduct("widget", "/img/w.jpg", "Smart Widget", "Pro",
                        "test", "Gallery"),
                new SampleProduct("other-p", "/img/o.jpg", "Other", null,
                        "misc", "Misc")));
        ctx.setVariable("news", List.of(
                new SampleArticle("a", "Alpha News", null, null,
                        LocalDateTime.of(2026, 1, 1, 9, 0), "c-news", "Company News"),
                new SampleArticle("b", "Beta News", null, null,
                        LocalDateTime.of(2026, 1, 2, 9, 0), "other", "Industry")));
        return ctx;
    }

    @Test
    void rendersFilteredNewsAndProductBlocks() {
        String html = templateEngine.process("themes/modern/info", ctx());

        // c-news 过滤板块:标题取分类名,含 Alpha News,链接 /news/a,日期格式化
        assertThat(html).contains("Company News");
        assertThat(html).contains("Alpha News");
        assertThat(html).contains("/news/a");
        assertThat(html).contains("2026-01-01");
        // 不含 other 分类的 Beta News(c-news 过滤)
        assertThat(html).doesNotContain("Beta News");
        // test 产品板块:含 Smart Widget,链接 /products/widget
        assertThat(html).contains("Gallery");
        assertThat(html).contains("Smart Widget");
        assertThat(html).contains("/products/widget");
        // 不含 misc 分类的 Other 产品
        assertThat(html).doesNotContain("Other");
    }
}
