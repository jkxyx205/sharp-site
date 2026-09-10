package com.rick.site.admin.security;

import com.rick.site.admin.entity.AdminUser;
import com.rick.site.admin.service.AdminUserService;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 登录用户加载(TASK-0902):按 (上下文租户, username) 查 admin_user。
 *
 * <p>登录请求先经 TenantFilter(@Order(-200),在 security 之前)由 Host 解析租户写入
 * TenantContext,本服务据此定位租户;admin_user 的 tenant_id 过滤由 SiteDatabaseConfig
 * 统一追加。未找到或停用 → UsernameNotFoundException。
 *
 * @author Rick.Xu
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserService adminUserService;

    public AdminUserDetailsService(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 登录按租户域名:无租户上下文(如通过 localhost/IP 访问后台)时,
        // requireTenantId() 会抛 BizException → 被 DaoAuthenticationProvider 包装成
        // InternalAuthenticationServiceException(500)。改为抛 UsernameNotFoundException,
        // 使表单登录失败优雅重定向到 /admin/login?error(与密码错误一致)。
        if (TenantContext.get().isEmpty()) {
            throw new UsernameNotFoundException("当前请求未解析到租户，请通过租户域名访问后台");
        }
        AdminUser user = adminUserService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("管理员不存在: " + username));
        return new AdminPrincipal(user.getId(), user.getTenantId(),
                user.getUsername(), user.getPasswordHash(),
                user.getStatus() != null && user.getStatus() == 1);
    }
}
