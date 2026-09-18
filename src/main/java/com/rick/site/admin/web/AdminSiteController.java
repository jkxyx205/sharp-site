package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.admin.service.SiteManagementService;
import com.rick.site.admin.service.SiteManagementService.SiteCreateForm;
import com.rick.site.tenant.entity.Tenant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 后台「网站管理」:跨租户的站点列表、开通与拆除。
 *
 * <p>列表({@code GET /admin/sites})展示全部 tenant;开通({@code GET /admin/sites/new}
 * + {@code POST /admin/sites})填 tenant / tenant_domain / admin_user 三表初始化站点;
 * 拆除({@code POST /admin/sites/{id}/delete})按 tenant 物理级联删除全部数据。
 * 操作委托 {@link SiteManagementService}。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/sites")
public class AdminSiteController {

    private final SiteManagementService siteService;

    public AdminSiteController(SiteManagementService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public String list(Model model) {
        List<Tenant> sites = siteService.list();
        model.addAttribute("sites", sites);
        return "admin/sites";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("themes", siteService.themeIds());
        return "admin/site-form";
    }

    @PostMapping
    public String create(@ModelAttribute SiteCreateForm form, RedirectAttributes ra) {
        try {
            Tenant created = siteService.create(form);
            ra.addFlashAttribute("msg", "站点已创建: " + created.getName()
                    + " (id=" + created.getId() + ")");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sites";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            siteService.delete(id);
            ra.addFlashAttribute("msg", "站点已删除: id=" + id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sites";
    }
}
