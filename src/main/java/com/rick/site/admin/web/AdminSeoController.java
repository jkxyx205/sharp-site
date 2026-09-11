package com.rick.site.admin.web;

import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.news.service.ArticleService;
import com.rick.site.page.service.SitePageService;
import com.rick.site.product.service.ProductService;
import com.rick.site.seo.entity.SeoConfig;
import com.rick.site.seo.service.SeoConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 后台 SEO 元数据管理(基本数据·多语言):按 (pageType, pageId, language) 编辑一行。
 *
 * <p>复用 {@link SeoConfigService#listByTenant()} / {@link SeoConfigService#find} /
 * {@link SeoConfigService#save}(幂等 upsert)。多语言经语种下拉单语种编辑:GET 带
 * {@code ?pageType=&pageId=&lang=} 定位某行,保存仅写该 (pageType,pageId,language)。
 *
 * <p>pageId 仅对 page/product/article 类型有效;home/products_list/news_list 恒为 null。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/seo")
public class AdminSeoController {

    private final SeoConfigService seoConfigService;
    private final ProductService productService;
    private final ArticleService articleService;
    private final SitePageService pageService;
    private final DefaultLocaleResolver localeResolver;

    public AdminSeoController(SeoConfigService seoConfigService, ProductService productService,
                             ArticleService articleService, SitePageService pageService,
                             DefaultLocaleResolver localeResolver) {
        this.seoConfigService = seoConfigService;
        this.productService = productService;
        this.articleService = articleService;
        this.pageService = pageService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("configs", seoConfigService.listByTenant());
        return "admin/seo";
    }

    @GetMapping("/edit")
    public String form(@RequestParam String pageType,
                       @RequestParam(required = false) Long pageId,
                       @RequestParam(required = false) String lang,
                       Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
        SeoConfig config = seoConfigService.find(pageType, pageId, language).orElseGet(() -> {
            SeoConfig c = new SeoConfig();
            c.setPageType(pageType);
            c.setPageId(pageId);
            c.setLanguage(language);
            return c;
        });
        model.addAttribute("config", config);
        model.addAttribute("currentLang", language);
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
        // 实体选择下拉(仅 page/product/article 类型有用)
        model.addAttribute("pages", pageService.listByTenant());
        model.addAttribute("products", productService.listByTenant());
        model.addAttribute("articles", articleService.listByTenant());
        return "admin/seo-form";
    }

    @PostMapping("/save")
    public String save(SeoConfig config) {
        SeoConfig saved = seoConfigService.save(config); // upsert by page_type+pageId+language
        Long pageId = saved.getPageId();
        return "redirect:/admin/seo/edit?pageType=" + saved.getPageType()
                + (pageId != null ? "&pageId=" + pageId : "")
                + "&lang=" + saved.getLanguage();
    }
}
