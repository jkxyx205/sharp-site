package com.rick.site.tenant.service;

import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0103 验收测试:企业固定信息 upsert、每租户一行、tenantId 归属不可伪造。
 * tenant_id 由 TenantContext 注入(DatabaseConfig 统一追加/注入),事务自动回滚。
 */
@SpringBootTest
@Transactional
class TenantConfigServiceTest {

    @Autowired
    private TenantConfigService configService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").build());
        TenantContext.set(t);
        return t;
    }

    @Test
    void firstSaveCreatesConfig() {
        Tenant a = createTenant("tc-a");
        TenantConfig saved = configService.save(TenantConfig.builder()
                .companyName("Acme Corp")
                .companyNameShort("Acme")
                .email("info@acme.com")
                .phone("+1-555-0100")
                .address("1 Main St")
                .copyright("© 2026 Acme Corp")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(a.getId());
        assertThat(saved.getBaseEntityInfo().getCreateTime()).isNotNull();
        assertThat(saved.getBaseEntityInfo().getUpdateTime()).isNotNull();
        assertThat(saved.getBaseEntityInfo().getCreateBy()).isEqualTo(1L);

        TenantConfig loaded = configService.findByTenant().orElseThrow();
        assertThat(loaded.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(loaded.getEmail()).isEqualTo("info@acme.com");
    }

    @Test
    void secondSaveUpdatesSameRow() {
        Tenant a = createTenant("tc-b");
        TenantConfig first = configService.save(TenantConfig.builder()
                .companyName("Old Name").build());
        TenantConfig second = configService.save(TenantConfig.builder()
                .companyName("New Name").companyNameShort("NN").build());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getBaseEntityInfo().getCreateTime()).isEqualTo(first.getBaseEntityInfo().getCreateTime());
        // 全列更新:未提交的字段被清空是预期语义
        TenantConfig loaded = configService.findByTenant().orElseThrow();
        assertThat(loaded.getCompanyName()).isEqualTo("New Name");
        assertThat(loaded.getCompanyNameShort()).isEqualTo("NN");
    }

    @Test
    void configIsIsolatedPerTenant() {
        Tenant a = createTenant("tc-c");
        configService.save(TenantConfig.builder().companyName("A Co").build());
        Tenant b = createTenant("tc-d");
        configService.save(TenantConfig.builder().companyName("B Co").build());

        TenantContext.set(a);
        assertThat(configService.findByTenant().orElseThrow().getCompanyName()).isEqualTo("A Co");
        TenantContext.set(b);
        assertThat(configService.findByTenant().orElseThrow().getCompanyName()).isEqualTo("B Co");
    }

    @Test
    void entityTenantIdIsOverriddenByContextValue() {
        Tenant a = createTenant("tc-e");
        Tenant b = createTenant("tc-f");
        // 上下文为 A,实体上伪造 tenantId=B;框架注入覆盖,以 A 为准
        TenantContext.set(a);
        TenantConfig saved = configService.save(TenantConfig.builder()
                .tenantId(b.getId())
                .companyName("Spoofed")
                .build());

        assertThat(saved.getTenantId()).isEqualTo(a.getId());
        TenantContext.set(b);
        assertThat(configService.findByTenant()).isEmpty();
        TenantContext.set(a);
        assertThat(configService.findByTenant()).isPresent();
    }

    @Test
    void findByTenantReturnsEmptyWhenAbsent() {
        Tenant c = createTenant("tc-g");
        // 该租户从未保存 config
        assertThat(configService.findByTenant()).isEmpty();
    }
}
