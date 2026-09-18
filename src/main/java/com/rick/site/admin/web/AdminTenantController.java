package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantService;
import com.rick.site.theme.service.ThemeManifestResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台「网站设置」:维护当前 admin 所属租户的基本信息。
 *
 * <p>仅可编辑 name / themeId / status;id 与 code 只读(身份标识不可改,表单不回传、
 * 服务端亦不接收)。themeId 下拉项由 {@link ThemeManifestResolver#listThemeIds()}
 * 扫描各主题的 {@code meta/theme.json} 得到,只列真实存在的主题。
 * 操作对象恒为 {@link TenantContext} 中的当前租户(无 id 入参,防 IDOR)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/settings")
public class AdminTenantController {

    private final TenantService tenantService;
    private final ThemeManifestResolver manifestResolver;

    public AdminTenantController(TenantService tenantService, ThemeManifestResolver manifestResolver) {
        this.tenantService = tenantService;
        this.manifestResolver = manifestResolver;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("tenant", TenantContext.require());
        model.addAttribute("themes", manifestResolver.listThemeIds());
        return "admin/settings";
    }

    @PostMapping("/save")
    public String save(@RequestParam String name,
                       @RequestParam String themeId,
                       @RequestParam Short status,
                       RedirectAttributes ra) {
        try {
            if (name == null || name.isBlank()) {
                throw new BizException("租户名称不能为空");
            }
            if (!manifestResolver.listThemeIds().contains(themeId)) {
                throw new BizException("未知主题: " + themeId);
            }
            Tenant current = TenantContext.require();
            // 重新从 DB 加载当前租户再覆盖可编辑字段;id/code 由现有实体携带、不被入参触碰。
            Tenant tenant = tenantService.findById(current.getId())
                    .orElseThrow(() -> new BizException("租户不存在: id=" + current.getId()));
            tenant.setName(name);
            tenant.setThemeId(themeId);
            tenant.setStatus(status);
            tenantService.save(tenant);
            ra.addFlashAttribute("msg", "保存成功");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
