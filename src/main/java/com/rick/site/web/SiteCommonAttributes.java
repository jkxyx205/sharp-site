package com.rick.site.web;

import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LanguageOption;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.service.TenantConfigService;
import com.rick.site.theme.model.ThemeManifest;
import com.rick.site.theme.service.ThemeManifestResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 前台公共模型属性(CLAUDE.md §15 / §6 数据与模板分离)。
 *
 * <p>为所有前台 Controller 注入 siteName / config / currentLanguage / languages(切换链接),
 * 避免每个 Controller 重复装配。无租户上下文时(未解析到租户的请求)跳过,
 * 由各 Controller 决定 404/引导策略。languages 仅前台页面装配(admin/preview 各自处理)。
 *
 * @author Rick.Xu
 */
@ControllerAdvice
public class SiteCommonAttributes {

    private final TenantConfigService tenantConfigService;
    private final DefaultLocaleResolver localeResolver;
    private final ThemeManifestResolver manifestResolver;

    public SiteCommonAttributes(TenantConfigService tenantConfigService, DefaultLocaleResolver localeResolver,
                               ThemeManifestResolver manifestResolver) {
        this.tenantConfigService = tenantConfigService;
        this.localeResolver = localeResolver;
        this.manifestResolver = manifestResolver;
    }

    @ModelAttribute
    public void populate(HttpServletRequest request, Model model) {
        Optional<Tenant> tenantOpt = TenantContext.get();
        if (tenantOpt.isEmpty()) {
            return;
        }
        Tenant tenant = tenantOpt.get();
        TenantConfig config = tenantConfigService.findByTenant().orElse(null);
        String siteName = (config != null && config.getCompanyName() != null)
                ? config.getCompanyName() : tenant.getName();
        model.addAttribute("siteName", siteName);
        model.addAttribute("config", config);
        String currentLanguage = LocaleContext.get().map(r -> r.language()).orElse(null);
        model.addAttribute("currentLanguage", currentLanguage);

        // 语言切换链接仅装配前台页面;admin(自带 languages List<String>)与 preview(?lang= 参数)各自处理
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/preview")) {
            return;
        }
        ThemeManifest manifest;
        try {
            manifest = manifestResolver.resolve(tenant);
        } catch (Exception e) {
            model.addAttribute("localePrefix", "");
            return;
        }
        // localePrefix:默认语种无前缀(根),其余语种带小写前缀(/{locale});单语言恒为 ""
        String localePrefix = "";
        if (manifest.isMultiLanguage() && currentLanguage != null
                && !currentLanguage.equals(manifest.defaultLocale())) {
            localePrefix = "/" + currentLanguage.toLowerCase(Locale.ROOT);
        }
        model.addAttribute("localePrefix", localePrefix);
        model.addAttribute("languages", buildLanguageOptions(tenant, manifest));
    }

    /**
     * 构造当前页各语言切换链接。默认语种无前缀(如 {@code /products}),
     * 其他语种带小写前缀(如 {@code /zh-cn/products},匹配路由 {@code /[a-z]{2}-[a-z]{2}}/...)。
     * 单语言主题不渲染切换(返回空列表)。
     */
    private List<LanguageOption> buildLanguageOptions(Tenant tenant, ThemeManifest manifest) {
        if (!manifest.isMultiLanguage()) {
            return List.of();
        }
        LocaleResolution loc = LocaleContext.get().orElse(null);
        String effective = (loc != null) ? loc.effectivePath() : "/";
        String defaultLanguage = manifest.defaultLocale();
        List<LanguageOption> options = new ArrayList<>();
        for (String lang : manifest.locales()) {
            String path = lang.equals(defaultLanguage)
                    ? effective
                    : "/" + lang.toLowerCase(Locale.ROOT) + effective;
            options.add(new LanguageOption(lang, label(lang), path));
        }
        return options;
    }

    /** 语言展示文案;新增语言在此追加。 */
    private static String label(String lang) {
        return switch (lang) {
            case "zh-CN" -> "中文";
            case "en-US" -> "EN";
            default -> lang;
        };
    }
}
