package com.rick.site.admin.web;

import com.rick.site.admin.security.AdminPrincipal;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * 为所有后台控制器注入顶栏所需公共模型:username / tenantName。
 *
 * <p>避免每个 admin 控制器重复 addAttribute;topbar 片段统一读取。
 * username 取自认证主体(AdminPrincipal),tenantName 取自 AdminContextFilter 写入的 TenantContext。
 *
 * @author Rick.Xu
 */
@ControllerAdvice("com.rick.site.admin.web")
public class AdminGlobalModel {

    @ModelAttribute
    public void populate(Principal principal, Model model) {
        String username = (principal instanceof AdminPrincipal a) ? a.getUsername() : "";
        String tenantName = TenantContext.get().map(t -> t.getName()).orElse("");
        model.addAttribute("username", username);
        model.addAttribute("tenantName", tenantName);
    }
}
