package com.rick.site.common.model;

import com.rick.db.repository.Column;
import com.rick.db.repository.model.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 租户业务实体的统一基类:在 {@link BaseEntity} 审计列(create_by/create_time/
 * update_by/update_time/is_deleted)之上增加不可变的 tenant_id 列。
 *
 * <p>tenant_id 由 {@code SiteDatabaseConfig} 的 TableDAO/InsertUpdateCallback
 * 从 TenantContext 自动注入,禁止前端篡改(CLAUDE.md §4)。
 *
 * @author Rick.Xu
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TenantBaseEntity<ID> extends BaseEntity<ID> implements TenantIdGetter {

    @Column(value = "tenant_id", updatable = false, nullable = false, comment = "租户ID")
    private Long tenantId;
}
