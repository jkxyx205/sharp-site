package com.rick.site.common.config;

import com.rick.site.common.context.AdminUserContext;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一字段处理验收测试(SiteDatabaseConfig)。
 *
 * <p>验证:insert 自动填充 create_by/create_time/update_by/update_time/is_deleted,
 * 用户来自 AdminUserContext(空回退 1L);update 自动刷新 update_time 且保留 create_time;
 * deleteById 为逻辑删除;tenant_id 由 TenantContext 注入(防伪造)。
 */
@SpringBootTest
@Transactional
class UnifiedFieldsTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        AdminUserContext.clear();
    }

    @Test
    void insertAutoFillsAuditAndLogicalDeleteFlag() {
        Tenant tenant = tenantService.save(Tenant.builder()
                .code("audit").name("Audit").themeId("modern").build());

        assertThat(tenant.getBaseEntityInfo()).isNotNull();
        assertThat(tenant.getBaseEntityInfo().getCreateTime()).isNotNull();
        assertThat(tenant.getBaseEntityInfo().getUpdateTime()).isNotNull();
        assertThat(tenant.getBaseEntityInfo().getCreateBy()).isEqualTo(1L);
        assertThat(tenant.getBaseEntityInfo().getDeleted()).isFalse();
    }

    @Test
    void createBySourcedFromAdminUserContext() {
        AdminUserContext.setUserId(42L);
        try {
            Tenant t = tenantService.save(Tenant.builder()
                    .code("ctx").name("Ctx").themeId("modern").build());
            TenantContext.set(t);
            TenantDomain d = domainService.add("ctx.example.com", false);
            assertThat(d.getBaseEntityInfo().getCreateBy()).isEqualTo(42L);
        } finally {
            AdminUserContext.clear();
        }
    }

    @Test
    void updateRefreshesUpdateTimeAndPreservesCreateTime() throws InterruptedException {
        Tenant t = tenantService.save(Tenant.builder()
                .code("refresh").name("Refresh").themeId("modern").build());
        TenantContext.set(t);
        TenantDomain d = domainService.add("refresh.com", false);
        LocalDateTime createTime = d.getBaseEntityInfo().getCreateTime();

        Thread.sleep(5);
        domainService.updateStatus(d.getId(), (short) 0);

        TenantDomain reloaded = domainService.selectById(d.getId()).orElseThrow();
        assertThat(reloaded.getBaseEntityInfo().getUpdateTime()).isAfter(createTime);
        assertThat(reloaded.getBaseEntityInfo().getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void daoOverridesTenantIdFromContext() {
        Tenant tenantA = tenantService.save(Tenant.builder()
                .code("owner").name("Owner").themeId("modern").build());
        TenantContext.set(tenantA);
        try {
            // tenant_id 由框架从上下文注入(add 不再接受 tenantId 参数),实体无法伪造归属
            TenantDomain d = domainService.add("spoof.example.com", false);
            assertThat(d.getTenantId()).isEqualTo(tenantA.getId());
            assertThat(domainService.listByTenant())
                    .anySatisfy(row -> assertThat(row.getDomain()).isEqualTo("spoof.example.com"));
            // 切换到另一租户:看不到该域名(数据归属 tenantA)
            Tenant tenantB = tenantService.save(Tenant.builder()
                    .code("other").name("Other").themeId("modern").build());
            TenantContext.set(tenantB);
            assertThat(domainService.listByTenant()).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }
}
