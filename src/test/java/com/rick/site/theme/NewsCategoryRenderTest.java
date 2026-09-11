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
 * 回归:news.html 「c-news 过滤板块」+「遍历分类、显示分类下新闻」板块。
 *
 * <p>对应 products 的同类陷阱:SpEL 选择 {@code allNews.?[categorySlug == cat.slug]} 不可用
 * (th:each 变量在选择表达式内不可解析)。news.html 改用投影 + {@code #lists.contains} +
 * 每条 {@code th:if} 过滤。本测试注入 categories + allNews,确保板块渲染正确、不抛异常。
 */
@SpringBootTest
class NewsCategoryRenderTest {

    @Autowired
    private TemplateEngine templateEngine;

    record SampleArticle(String slug, String title, String summary, String content, LocalDateTime publishTime,
                         String categorySlug, String categoryName) {
    }

    record CatView(String slug, String name) {
    }

    private WebContext ctx() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/news");
        req.setContextPath("");
        MockServletContext sc = new MockServletContext();
        JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(sc);
        WebContext ctx = new WebContext(app.buildExchange(req, new MockHttpServletResponse()), req.getLocale(), null);
        ctx.setVariable("siteName", "Acme");
        ctx.setVariable("pageTitle", "News");
        ctx.setVariable("pageDescription", "");
        ctx.setVariable("pageKeywords", "");
        ctx.setVariable("canonical", "https://acme.example.com/news");
        ctx.setVariable("robots", "index, follow");
        ctx.setVariable("localePrefix", "");
        List<SampleArticle> all = List.of(
                new SampleArticle("a", "Alpha News", null, null, LocalDateTime.of(2026, 1, 1, 9, 0), "c-news", "Company News"),
                new SampleArticle("b", "Beta News", null, null, LocalDateTime.of(2026, 1, 2, 9, 0), "other", "Industry"),
                new SampleArticle("c", "Gamma News", null, null, LocalDateTime.of(2026, 1, 3, 9, 0), "c-news", "Company News"));
        ctx.setVariable("news", all);
        ctx.setVariable("allNews", all);
        ctx.setVariable("categories", List.of(
                new CatView("c-news", "Company News"), new CatView("other", "Industry"),
                new CatView("empty", "Empty"))); // 无新闻的分类应被隐藏
        return ctx;
    }

    @Test
    void rendersCategorySectionsWithNews() {
        String html = templateEngine.process("themes/modern/news", ctx());

        // c-news 过滤板块:标题取分类名,含两条 c-news 新闻
        assertThat(html).contains("Company News");
        assertThat(html).contains("Alpha News").contains("Gamma News");
        // 全分类遍历:Industry 分类(其他)也出现
        assertThat(html).contains("Beta News");
        // 无新闻的分类不应渲染
        assertThat(html).doesNotContain("Empty");
        // 链接正确(localePrefix=""):指向 /news/{slug}
        assertThat(html).contains("/news/a").contains("/news/b").contains("/news/c");
        // 发布日期格式化
        assertThat(html).contains("2026-01-01");
    }
}
