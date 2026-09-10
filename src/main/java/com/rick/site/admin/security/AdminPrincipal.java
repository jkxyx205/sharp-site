package com.rick.site.admin.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 认证主体(TASK-0902):携带 adminUserId / tenantId / username,供会话存储。
 *
 * <p>tenantId 为后台请求的权威租户来源(非 Host),AdminContextFilter 据此覆盖
 * TenantContext,防止管理员在其它租户域名下越权读取(§3/§4)。
 * {@code getPassword()} 返回 password_hash 供 DaoAuthenticationProvider 校验。
 * toString 不输出 password_hash(§20)。
 *
 * @author Rick.Xu
 */
public class AdminPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;
    private static final GrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    private final Long adminUserId;
    private final Long tenantId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;

    public AdminPrincipal(Long adminUserId, Long tenantId, String username, String passwordHash, boolean enabled) {
        this.adminUserId = adminUserId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(ROLE_ADMIN);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "AdminPrincipal{adminUserId=" + adminUserId + ", tenantId=" + tenantId
                + ", username=" + username + ", enabled=" + enabled + "}";
    }
}
