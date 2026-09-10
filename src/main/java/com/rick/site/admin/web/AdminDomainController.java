package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 后台域名管理 Controller(TASK-1401)。
 *
 * <p>添加 / 删除 / 设置主域名,复用 {@link TenantDomainService}(上下文租户隔离 §4)。
 * 校验失败(域名格式/占用)经 RedirectAttributes 回显,不向用户抛 500。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/domains")
public class AdminDomainController {

    private static final Logger log = LoggerFactory.getLogger(AdminDomainController.class);

    private final TenantDomainService domainService;

    public AdminDomainController(TenantDomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping
    public String list(Model model) {
        List<TenantDomain> domains = domainService.listByTenant();
        model.addAttribute("domains", domains);
        return "admin/domains";
    }

    @PostMapping
    public String add(@RequestParam String domain,
                      @RequestParam(name = "primary", required = false) boolean primary,
                      RedirectAttributes redirect) {
        try {
            domainService.add(domain, primary);
        } catch (BizException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/domains";
    }

    @PostMapping("/{id}/primary")
    public String setPrimary(@PathVariable Long id) {
        domainService.setPrimary(id);
        return "redirect:/admin/domains";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            domainService.delete(id);
        } catch (BizException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/domains";
    }
}
