package com.rick.site.web;

import com.rick.site.i18n.context.LocaleContext;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.service.TenantConfigService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/**
 * 前台公共模型属性(CLAUDE.md §15 / §6 数据与模板分离)。
 *
 * <p>为所有前台 Controller 注入 siteName / config / currentLanguage,
 * 避免每个 Controller 重复装配。无租户上下文时(未解析到租户的请求)跳过,
 * 由各 Controller 决定 404/引导策略。
 *
 * @author Rick.Xu
 */
@ControllerAdvice
public class SiteCommonAttributes {

    private final TenantConfigService tenantConfigService;

    public SiteCommonAttributes(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    @ModelAttribute
    public void populate(Model model) {
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
        model.addAttribute("currentLanguage", LocaleContext.get().map(r -> r.language()).orElse(null));
    }
}
