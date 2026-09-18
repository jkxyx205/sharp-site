package com.rick.site.web;

import com.rick.site.i18n.SupportedLanguage;
import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.i18n.model.LanguageOption;
import com.rick.site.i18n.model.LocaleResolution;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.dto.TenantConfigView;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantConfigService;
import com.rick.site.theme.model.ThemeManifest;
import com.rick.site.theme.service.ThemeManifestResolver;
import com.rick.site.theme.service.ThemeResolver;
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
 * <p>为所有前台 Controller 注入 siteName / config / currentLanguage / themeId / languages(切换链接),
 * 避免每个 Controller 重复装配。无租户上下文时(未解析到租户的请求)跳过,
 * 由各 Controller 决定 404/引导策略。languages 仅前台页面装配(admin/preview 各自处理)。
 * {@code themeId} 供模板片段引用(如 {@code ~{themes/__${themeId}__/fragments/header :: ...}})
 * 以替代硬编码主题名,使复制主题目录后内部引用零改动;live/preview 均注入(admin 提前 return 前)。
 *
 * @author Rick.Xu
 */
@ControllerAdvice
public class SiteCommonAttributes {

    private final TenantConfigService tenantConfigService;
    private final DefaultLocaleResolver localeResolver;
    private final ThemeManifestResolver manifestResolver;
    private final ThemeResolver themeResolver;

    public SiteCommonAttributes(TenantConfigService tenantConfigService, DefaultLocaleResolver localeResolver,
                               ThemeManifestResolver manifestResolver, ThemeResolver themeResolver) {
        this.tenantConfigService = tenantConfigService;
        this.localeResolver = localeResolver;
        this.manifestResolver = manifestResolver;
        this.themeResolver = themeResolver;
    }

    @ModelAttribute
    public void populate(HttpServletRequest request, Model model) {
        Optional<Tenant> tenantOpt = TenantContext.get();
        if (tenantOpt.isEmpty()) {
            return;
        }
        Tenant tenant = tenantOpt.get();

        ThemeManifest manifest;
        try {
            manifest = manifestResolver.resolve(tenant);
        } catch (Exception e) {
            // 无主题清单时回退 tenant.name;config 留空
            model.addAttribute("siteName", tenant.getName());
            model.addAttribute("localePrefix", "");
            return;
        }
        String defaultLanguage = manifest.defaultLocale();
        String currentLanguage = LocaleContext.get().map(r -> r.language()).orElse(null);

        // 企业信息按当前语种解析(公司名等四字段 i18n,缺失回退默认语种;联系方式共享)
        TenantConfigView config = tenantConfigService
                .resolveForDisplay(currentLanguage != null ? currentLanguage : defaultLanguage, defaultLanguage)
                .orElse(null);
        String siteName = (config != null && config.companyName() != null)
                ? config.companyName() : tenant.getName();
        model.addAttribute("siteName", siteName);
        model.addAttribute("config", config);
        model.addAttribute("currentLanguage", currentLanguage);
        // themeId 供模板片段引用 ~{themes/__${themeId}__/fragments/...},替代硬编码主题名,
        // 使复制主题目录后内部 th:replace 引用零改动;live/preview 均注入(admin 路径提前 return 前)。
        model.addAttribute("themeId", themeResolver.resolveTheme(tenant));

        // 语言切换链接仅装配前台页面;admin(自带 languages List<String>)与 preview(?lang= 参数)各自处理
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/preview")) {
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

    /** 语言展示文案;派生自 {@link SupportedLanguage}(新增语言改一处)。 */
    private static String label(String lang) {
        return SupportedLanguage.labelOf(lang);
    }
}
