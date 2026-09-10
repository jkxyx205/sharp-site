package com.rick.site.admin.web;

import com.rick.site.admin.security.AdminPrincipal;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 后台认证与首页 Controller(TASK-0902)。
 *
 * <p>{@code GET /admin/login} 渲染登录页;{@code GET /admin/} 显示当前管理员与租户,
 * 证明认证已建立且租户来自认证主体(非 Host)。
 *
 * @author Rick.Xu
 */
@Controller
public class AdminAuthController {

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin/")
    public String index(Principal principal, Model model) {
        if (principal instanceof AdminPrincipal admin) {
            model.addAttribute("username", admin.getUsername());
            model.addAttribute("tenantId", admin.getTenantId());
        }
        model.addAttribute("tenantName",
                TenantContext.get().map(t -> t.getName()).orElse(""));
        return "admin/index";
    }
}
