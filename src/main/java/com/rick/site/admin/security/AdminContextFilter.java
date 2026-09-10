package com.rick.site.admin.security;

import com.rick.site.common.context.AdminUserContext;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.service.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 后台租户上下文过滤器(TASK-0902 / 0903)。
 *
 * <p>注册进 Spring Security 链(addFilterBefore UsernamePasswordAuthenticationFilter),
 * 在认证已建立后用管理员的 tenantId 覆盖 TenantContext、写 AdminUserContext.userId。
 * 后台请求的权威租户来自认证管理员而非 Host,防止管理员在其它租户域名下越权读取(§3/§4)。
 * 登录请求(无认证主体)跳过,由 TenantFilter 写入的 Host 上下文供 UserDetailsService 使用。
 * 不在此清理 ThreadLocal —— 由最外层 TenantFilter(@Order(-200))的 finally 兜底。
 *
 * @author Rick.Xu
 */
public class AdminContextFilter extends OncePerRequestFilter {

    private final TenantService tenantService;

    public AdminContextFilter(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
            tenantService.findById(principal.getTenantId()).ifPresent(tenant -> {
                TenantContext.set(tenant);
                AdminUserContext.setUserId(principal.getAdminUserId());
            });
        }
        filterChain.doFilter(request, response);
    }
}
