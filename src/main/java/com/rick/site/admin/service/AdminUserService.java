package com.rick.site.admin.service;

import com.rick.db.plugin.BaseServiceImpl;
import com.rick.site.admin.dao.AdminUserDAO;
import com.rick.site.admin.entity.AdminUser;
import com.rick.site.tenant.context.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理员用户服务(TASK-0901)。
 *
 * <p>密码以 BCrypt 摘要存储,禁止明文(§20 禁止日志输出 password_hash)。
 * tenant_id 由框架从 TenantContext 注入;按 (上下文租户, username) 查询登录用户,
 * tenant_id 过滤由 SiteDatabaseConfig 统一追加。
 *
 * @author Rick.Xu
 */
@Service
@Validated
public class AdminUserService extends BaseServiceImpl<AdminUserDAO, AdminUser, Long> {

    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AdminUserDAO baseDAO, PasswordEncoder passwordEncoder) {
        super(baseDAO);
        this.passwordEncoder = passwordEncoder;
    }

    /** 创建管理员:明文密码经 BCrypt 哈希后存储,租户取自上下文,状态默认启用。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminUser create(String username, String rawPassword) {
        TenantContext.requireTenantId();
        AdminUser user = AdminUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status((short) 1)
                .build();
        return baseDAO.insert(user);
    }

    /** 按用户名查当前上下文租户的管理员(tenant_id 由框架追加)。 */
    public Optional<AdminUser> findByUsername(String username) {
        List<AdminUser> found = baseDAO.select("username = :username", Map.of("username", username));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}
