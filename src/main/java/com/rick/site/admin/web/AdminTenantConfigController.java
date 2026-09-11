package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.service.TenantConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台企业固定信息管理(基本数据·非语言):每租户一行,直接编辑,无语种维度。
 *
 * <p>复用 {@link TenantConfigService#findByTenant()} 与 {@link TenantConfigService#save}
 * (按上下文租户幂等 upsert,tenant_id 由框架注入防伪造)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/company")
public class AdminTenantConfigController {

    private final TenantConfigService tenantConfigService;

    public AdminTenantConfigController(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    @GetMapping
    public String form(Model model) {
        TenantConfig config = tenantConfigService.findByTenant().orElseGet(TenantConfig::new);
        model.addAttribute("config", config);
        return "admin/company-form";
    }

    @PostMapping("/save")
    public String save(TenantConfig config, RedirectAttributes ra) {
        try {
            tenantConfigService.save(config);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/company";
    }
}
