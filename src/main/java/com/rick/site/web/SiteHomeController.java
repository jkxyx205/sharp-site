package com.rick.site.web;

import com.rick.site.home.service.HomeSectionService;
import com.rick.site.home.service.HomeSectionService.ResolvedSection;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.seo.dto.SeoFallback;
import com.rick.site.seo.service.SeoConfigService;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 首页 Controller(TASK-0502 / TASK-1002)。
 *
 * <p>渲染 modern 首页:从首页区块(可后台编辑)取 hero/company/cta 文案,
 * 产品/新闻列表由 Phase 6/7 注入,当前阶段传空(模板有 th:if 空态)。
 * 语言来自 LocaleContext;默认语言 {@code /},其他语言 {@code /{locale}}。
 * SEO meta 由 {@link SeoConfigService} 解析(seo_config 覆盖,缺失回退区块文案/请求 URL)。
 *
 * @author Rick.Xu
 */
@Controller
public class SiteHomeController {

    private final HomeSectionService homeSectionService;
    private final SeoConfigService seoService;
    private final ThemeManifestResolver manifestResolver;

    public SiteHomeController(HomeSectionService homeSectionService, SeoConfigService seoService,
                             ThemeManifestResolver manifestResolver) {
        this.homeSectionService = homeSectionService;
        this.seoService = seoService;
        this.manifestResolver = manifestResolver;
    }

    @GetMapping(value = {"/", "/{locale:[a-z]{2}-[a-z]{2}}", "/{locale:[a-z]{2}-[a-z]{2}}/"})
    public String index(HttpServletRequest request, Model model) {
        Tenant tenant = TenantContext.require();
        String defaultLanguage = manifestResolver.defaultLocale(tenant);
        LocaleResolution loc = LocaleContext.get()
                .orElseGet(() -> new LocaleResolution(defaultLanguage, "/"));
        Map<String, ResolvedSection> sections = homeSectionService.resolveForDisplay(
                loc.language(), defaultLanguage);

        ResolvedSection hero = sections.get("hero");
        ResolvedSection company = sections.get("company");
        ResolvedSection cta = sections.get("cta");

        model.addAttribute("tagline", text(hero, SiteHomeController::i18nTitle));
        model.addAttribute("intro", text(hero, SiteHomeController::i18nSubtitle));
        model.addAttribute("aboutTeaser", text(company, SiteHomeController::i18nContent));
        model.addAttribute("ctaTitle", text(cta, SiteHomeController::i18nTitle));
        // 产品/新闻列表由 Phase 6/7 注入,当前为空态
        model.addAttribute("products", List.of());
        model.addAttribute("news", List.of());

        String fbTitle = text(hero, SiteHomeController::i18nTitle);
        String fbDesc = text(company, SiteHomeController::i18nSubtitle);
        seoService.resolveView(SeoConfigService.HOME, null, loc.language(), defaultLanguage,
                new SeoFallback(fbTitle, fbDesc, "", request.getRequestURL().toString())).applyTo(model);
        return "themes/modern/index";
    }

    /** 区块 i18n 存在则取其字段,否则空串。 */
    private String text(ResolvedSection section, java.util.function.Function<ResolvedSection, String> getter) {
        return section != null ? getter.apply(section) : "";
    }

    // ---- 从 ResolvedSection 取 i18n 字段的 helper(空安全)----

    @SuppressWarnings("unused")
    private static String i18nTitle(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getTitle() : "";
    }

    private static String i18nSubtitle(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getSubtitle() : "";
    }

    private static String i18nContent(ResolvedSection s) {
        return s.i18n() != null ? s.i18n().getContent() : "";
    }
}
