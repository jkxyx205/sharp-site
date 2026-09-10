package com.rick.site.common.model;

/**
 * 租户归属回调接口:由 {@code SiteDatabaseConfig} 的 InsertUpdateCallback
 * 在 insert/update 后回填 tenant_id 到实体对象,值来自 TenantContext(防伪造)。
 *
 * <p>实现类在实体上声明 {@code @Column(value="tenant_id", updatable=false)} 字段,
 * 并由 Lombok 生成 setter 以满足本接口。
 *
 * @author Rick.Xu
 */
public interface TenantIdGetter {

    void setTenantId(Long tenantId);
}
