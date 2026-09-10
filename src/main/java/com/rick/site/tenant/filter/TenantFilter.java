package com.rick.site.tenant.filter;

import com.rick.site.common.context.AdminUserContext;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.service.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户解析过滤器(ARCHITECTURE.md §3 请求流程第一环)。
 *
 * <p>每个请求:Host → TenantResolver → TenantContext.set;
 * finally 中无条件 clear TenantContext 与 AdminUserContext,保证线程复用不残留。
 *
 * <p>@Order(-200) 排在 Spring security(-100)之前,使登录阶段 UserDetailsService
 * 能读到由 Host 写入的 TenantContext;后台认证后由 AdminContextFilter(在 security 链内)
 * 用管理员 tenantId 覆盖 TenantContext。本过滤器为最外层,finally 兜底清理全部 ThreadLocal(§4)。
 * 前台对未解析到租户的请求不拦截(上下文为空继续放行);后台租户来自认证用户而非 Host。
 */
@Component
@Order(-200)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    public TenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            tenantResolver.resolveByHost(request.getServerName()).ifPresent(TenantContext::set);
            filterChain.doFilter(request, response);
        } finally {
            AdminUserContext.clear();
            TenantContext.clear();
        }
    }
}
