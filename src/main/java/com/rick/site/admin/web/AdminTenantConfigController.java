package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.entity.TenantConfigI18n;
import com.rick.site.tenant.service.TenantConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 后台企业固定信息管理。
 *
 * <p>base 行(logo/phone/email/…/icp,跨语种共享)每租户一行,直接编辑;company_name/
 * company_name_short/address/copyright 按语种维护,表单为「先选语种 → 单语种表单 →
 * 保存仅写该语种一行」(与 product-form 一致,Phase 19 修订)。i18n 保存以 company_name
 * 为必填,某语种 company_name 为空时跳过(保留展示回退默认语种)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/company")
public class AdminTenantConfigController {

    private final TenantConfigService tenantConfigService;
    private final DefaultLocaleResolver localeResolver;

    public AdminTenantConfigController(TenantConfigService tenantConfigService,
                                       DefaultLocaleResolver localeResolver) {
        this.tenantConfigService = tenantConfigService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String form(@RequestParam(required = false) String lang, Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
        TenantConfig config = tenantConfigService.findByTenant().orElseGet(TenantConfig::new);
        Map<String, TenantConfigI18n> i18nMap = (config.getId() != null)
                ? tenantConfigService.loadI18nMap(config.getId()) : Map.of();
        model.addAttribute("config", config);
        model.addAttribute("i18nMap", i18nMap);
        model.addAttribute("currentLang", language);
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
        return "admin/company-form";
    }

    @PostMapping("/save")
    public String save(TenantConfig config, HttpServletRequest req, RedirectAttributes ra) {
        String language = req.getParameter("language");
        try {
            TenantConfig saved = tenantConfigService.save(config);
            String companyName = req.getParameter("companyName");
            if (companyName != null && !companyName.isBlank()) {
                tenantConfigService.saveI18n(saved.getId(), TenantConfigI18n.builder()
                        .language(language)
                        .companyName(companyName)
                        .companyNameShort(req.getParameter("companyNameShort"))
                        .address(req.getParameter("address"))
                        .copyright(req.getParameter("copyright"))
                        .build());
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/company?lang=" + language;
    }
}
