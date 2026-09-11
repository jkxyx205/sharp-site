package com.rick.site.admin.web;

import com.rick.site.admin.security.AdminPrincipal;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantDomainService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

/**
 * 为所有后台控制器注入顶栏所需公共模型:username / tenantName / tenantSiteUrl。
 *
 * <p>避免每个 admin 控制器重复 addAttribute;topbar 片段统一读取。
 * username 取自认证主体(AdminPrincipal),tenantName 取自 AdminContextFilter 写入的 TenantContext。
 * tenantSiteUrl 为租户主(自定义)域名指向的线上站点,与发布 {@code baseUrl} 同口径,
 * 顶栏「租户:xxx」据此渲染为可跳转链接;无域名时为空(渲染纯文本)。
 *
 * @author Rick.Xu
 */
@ControllerAdvice("com.rick.site.admin.web")
public class AdminGlobalModel {

    private final TenantDomainService domainService;

    public AdminGlobalModel(TenantDomainService domainService) {
        this.domainService = domainService;
    }

    @ModelAttribute
    public void populate(Principal principal, Model model) {
        String username = (principal instanceof AdminPrincipal a) ? a.getUsername() : "";
        String tenantName = TenantContext.get().map(t -> t.getName()).orElse("");
        model.addAttribute("username", username);
        model.addAttribute("tenantName", tenantName);
        model.addAttribute("tenantSiteUrl", TenantContext.get().map(t -> primarySiteUrl()).orElse(""));
    }

    /** 主域名(无则取第一个,再无则空)拼 https://,与 StaticSiteGenerator.baseUrl 同口径。 */
    private String primarySiteUrl() {
        List<TenantDomain> domains = domainService.listByTenant();
        String host = domains.stream()
                .filter(d -> d.getIsPrimary() != null && d.getIsPrimary() == 1)
                .map(TenantDomain::getDomain)
                .findFirst()
                .or(() -> domains.stream().map(TenantDomain::getDomain).findFirst())
                .orElse("");
        return host.isEmpty() ? "" : "https://" + host;
    }
}
