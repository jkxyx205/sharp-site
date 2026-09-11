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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归:products.html 「遍历分类、显示分类下产品」板块。
 *
 * <p>关键陷阱:SpEL 选择 {@code allProducts.?[categorySlug == cat.slug]} 会将列表元素作为根,
 * 导致 th:each 变量 {@code cat} 在选择表达式内不可解析(EL1008/EL1007)。改用投影
 * {@code allProducts.![categorySlug]} + {@code #lists.contains} 守卫 + 每卡片 {@code th:if} 过滤。
 * 本测试注入 categories + allProducts,确保该板块在真实数据下渲染正确、不抛异常。
 */
@SpringBootTest
class ProductCategorySpelDiagTest {

    @Autowired
    private TemplateEngine templateEngine;

    record SampleProduct(String slug, String name, String subtitle, String cover,
                          String content, String specificationJson,
                          String categorySlug, String categoryName) {
    }

    record CatView(String slug, String name) {
    }

    private WebContext ctx() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/products");
        req.setContextPath("");
        MockServletContext sc = new MockServletContext();
        JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(sc);
        WebContext ctx = new WebContext(app.buildExchange(req, new MockHttpServletResponse()), req.getLocale(), null);
        ctx.setVariable("siteName", "Acme");
        ctx.setVariable("pageTitle", "Products");
        ctx.setVariable("pageDescription", "");
        ctx.setVariable("pageKeywords", "");
        ctx.setVariable("canonical", "https://acme.example.com/products");
        ctx.setVariable("robots", "index, follow");
        ctx.setVariable("localePrefix", "");
        List<SampleProduct> all = List.of(
                new SampleProduct("a", "Alpha", null, "/img/a.jpg", null, null, "test", "Test Cat"),
                new SampleProduct("b", "Beta", null, "/img/b.jpg", null, null, "other", "Other Cat"),
                new SampleProduct("c", "Gamma", null, "/img/c.jpg", null, null, "test", "Test Cat"));
        ctx.setVariable("products", all);
        ctx.setVariable("allProducts", all);
        ctx.setVariable("categories", List.of(
                new CatView("test", "Test Cat"), new CatView("other", "Other Cat"),
                new CatView("empty", "Empty Cat"))); // 无产品的分类应被隐藏
        return ctx;
    }

    @Test
    void rendersCategorySectionsWithProducts() {
        String html = templateEngine.process("themes/modern/products", ctx());

        // 两个有产品的分类标题各出现一次
        assertThat(html).contains("Test Cat").contains("Other Cat");
        // 无产品的分类不应渲染
        assertThat(html).doesNotContain("Empty Cat");
        // 各分类下显示对应产品名
        assertThat(html).contains("Alpha").contains("Beta").contains("Gamma");
        // 链接前缀正确(localePrefix=""):指向 /products/{slug}
        assertThat(html).contains("/products/a").contains("/products/b").contains("/products/c");
    }
}
