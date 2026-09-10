package com.rick.site.tenant.entity;

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

/**
 * 租户域名(DATABASE.md §2)。
 *
 * <p>继承 {@link TenantBaseEntity}:tenant_id 不可变,由框架从 TenantContext 注入(防伪造);
 * 审计列自动填充,逻辑删除。约束:domain 全局唯一(uk_domain,逻辑删除后可重用);
 * 一个租户至多一个 primary(由 TenantDomainService 事务内保证)。domain 统一小写存储。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Table(value = "tenant_domain", comment = "租户域名")
public class TenantDomain extends TenantBaseEntity<Long> {

    @NotBlank
    @Column(nullable = false, comment = "域名,全局唯一,小写")
    String domain;

    @Column(value = "is_primary", nullable = false, comment = "是否主域名:1是,0否")
    Short isPrimary;

    @Column(nullable = false, comment = "状态:1启用,0停用")
    Short status;
}
