package com.rick.site.admin.web;

import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.seo.entity.SeoConfig;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.theme.model.ThemeManifest.ThemePage;
import com.rick.site.theme.service.ThemeManifestResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 后台 SEO 元数据管理(基本数据·多语言):按 (pageType, pageId, language) 编辑一行。
 *
 * <p>可维护 SEO 的页面清单<strong>取自当前租户主题清单</strong>
 * ({@code themes/{themeId}/meta/theme.json} 的 {@code pages}),不再硬编码在模板里。
 * 每个 page 即一个 SEO 槽:page_type = 页面路径(如 {@code /about}),page_id 恒为空。
 * 复用 {@link SeoConfigService#listByTenant()} / {@link SeoConfigService#find} /
 * {@link SeoConfigService#save}(幂等 upsert)。多语言经语种下拉单语种编辑:GET 带
 * {@code ?pageType=&lang=} 定位某行,保存仅写该 (pageType,language)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/seo")
public class AdminSeoController {

    private final SeoConfigService seoConfigService;
    private final DefaultLocaleResolver localeResolver;
    private final ThemeManifestResolver manifestResolver;

    public AdminSeoController(SeoConfigService seoConfigService,
                             DefaultLocaleResolver localeResolver,
                             ThemeManifestResolver manifestResolver) {
        this.seoConfigService = seoConfigService;
        this.localeResolver = localeResolver;
        this.manifestResolver = manifestResolver;
    }

    /** 当前租户主题声明的可维护 SEO 页面清单(无租户/解析失败回退空)。 */
    private List<ThemePage> pages() {
        return TenantContext.get().map(t -> {
            try {
                return manifestResolver.resolve(t).pages();
            } catch (Exception e) {
                return List.<ThemePage>of();
            }
        }).orElse(List.of());
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("configs", seoConfigService.listByTenant());
        model.addAttribute("pages", pages());
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
            c.setPageId(null); // 页面 SEO 恒无 page_id
            c.setLanguage(language);
            return c;
        });
        model.addAttribute("config", config);
        model.addAttribute("currentLang", language);
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
        model.addAttribute("pages", pages());
        return "admin/seo-form";
    }

    @PostMapping("/save")
    public String save(SeoConfig config) {
        config.setPageId(null); // 页面 SEO 恒无 page_id
        SeoConfig saved = seoConfigService.save(config); // upsert by page_type+language
        return "redirect:/admin/seo/edit?pageType=" + saved.getPageType()
                + "&lang=" + saved.getLanguage();
    }
}
