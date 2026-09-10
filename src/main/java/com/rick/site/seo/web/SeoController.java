package com.rick.site.seo.web;

import com.rick.site.news.service.ArticleService;
import com.rick.site.page.service.SitePageService;
import com.rick.site.product.service.ProductService;
import com.rick.site.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * SEO 站点级端点(TASK-1003 / 1004):sitemap.xml 与 robots.txt。
 *
 * <p>均按当前 Host 解析的租户作用域:列出租户首页、启用页面、上架产品、已发布新闻,
 * 用请求 scheme + Host 构造绝对 URL。未解析到租户时拒绝(BizException,与前台一致)。
 *
 * @author Rick.Xu
 */
@Controller
public class SeoController {

    private final ProductService productService;
    private final ArticleService articleService;
    private final SitePageService pageService;

    public SeoController(ProductService productService, ArticleService articleService,
                        SitePageService pageService) {
        this.productService = productService;
        this.articleService = articleService;
        this.pageService = pageService;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap(HttpServletRequest request) {
        TenantContext.require();
        String base = baseUrl(request);
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(sb, base + "/");
        pageService.listByTenant().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .forEach(p -> appendUrl(sb, base + p.getPath()));
        productService.listEnabled().forEach(p ->
                appendUrl(sb, base + "/products/" + esc(p.getSlug())));
        articleService.listPublished().forEach(a ->
                appendUrl(sb, base + "/news/" + esc(a.getSlug())));
        sb.append("</urlset>");
        return sb.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots(HttpServletRequest request) {
        TenantContext.require();
        String base = baseUrl(request);
        return "User-agent: *\n" +
                "Allow: /\n" +
                "\n" +
                "Sitemap: " + base + "/sitemap.xml\n";
    }

    private void appendUrl(StringBuilder sb, String loc) {
        sb.append("  <url><loc>").append(esc(loc)).append("</loc></url>\n");
    }

    private String baseUrl(HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (port > 0 && port != 80 && port != 443) {
            base += ":" + port;
        }
        return base;
    }

    /** 最小 XML 转义:& < >。URL 由 slug/path 构成,通常不含这些字符,此处仅作兜底。 */
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
