package com.rick.site.admin.entity;

import com.rick.db.repository.Column;
import com.rick.db.repository.Table;
import com.rick.site.common.model.TenantBaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Length;

/**
 * 管理员用户(DATABASE.md §17 / TASK-0901)。
 *
 * <p>租户内 username 唯一(uk_tenant_username);登录按所属租户域名定位租户后,
 * 再按 (tenant_id, username) 查询。继承 {@link TenantBaseEntity}:tenant_id 由框架
 * 从 TenantContext 注入,审计列自动填充,逻辑删除。password_hash 仅存 BCrypt 摘要,
 * 禁止明文(§20 禁止日志输出 password_hash)。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "admin_user", comment = "管理员用户")
public class AdminUser extends TenantBaseEntity<Long> {

    @NotBlank
    @Length(max = 100)
    @Column(nullable = false, comment = "登录用户名,租户内唯一")
    String username;

    @NotBlank
    @Length(max = 500)
    @Column(value = "password_hash", nullable = false, comment = "BCrypt 密码摘要,禁明文")
    String passwordHash;

    @Column(nullable = false, comment = "状态:1启用,0停用")
    Short status;
}
