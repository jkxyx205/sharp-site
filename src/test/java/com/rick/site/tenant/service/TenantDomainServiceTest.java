package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0102 验收测试:域名 CRUD、全局唯一、primary 规则、租户归属校验。
 * tenant_id 由 TenantContext 注入(DatabaseConfig 统一追加/注入);findByDomain 跨租户全局查询经 bypass。
 * 事务自动回滚,不在库中留测试数据。
 */
@SpringBootTest
@Transactional
class TenantDomainServiceTest {

    @Autowired
    private TenantDomainService domainService;

    @Autowired
    private TenantService tenantService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Tenant createTenant(String code) {
        Tenant t = tenantService.save(Tenant.builder()
                .code(code).name(code).themeId("modern").defaultLanguage("en-US").build());
        TenantContext.set(t);
        return t;
    }

    @Test
    void addNormalizesDomainAndAppliesDefaults() {
        Tenant a = createTenant("td-a");
        TenantDomain added = domainService.add("  A.Example.COM ", false);

        assertThat(added.getId()).isNotNull();
        assertThat(added.getDomain()).isEqualTo("a.example.com");
        assertThat(added.getTenantId()).isEqualTo(a.getId());
        assertThat(added.getIsPrimary()).isEqualTo((short) 0);
        assertThat(added.getStatus()).isEqualTo((short) 1);
        assertThat(added.getBaseEntityInfo().getCreateTime()).isNotNull();
        assertThat(added.getBaseEntityInfo().getCreateBy()).isEqualTo(1L);

        assertThat(domainService.findByDomain("a.example.com")).isPresent();
        // 查询同样走规范化
        assertThat(domainService.findByDomain("A.EXAMPLE.COM")).isPresent();
    }

    @Test
    void domainIsGloballyUniqueAcrossTenants() {
        Tenant a = createTenant("td-b");
        domainService.add("shared.com", false);
        Tenant b = createTenant("td-c");
        // B 添加已被 A 占用的域名:findByDomain 跨租户(bypass)命中 → 友好报错
        assertThatThrownBy(() -> domainService.add("shared.com", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("shared.com");
    }

    @Test
    void atMostOnePrimaryPerTenant() {
        Tenant a = createTenant("td-d");
        TenantDomain first = domainService.add("p1.com", true);
        TenantDomain second = domainService.add("p2.com", true);

        List<TenantDomain> domains = domainService.listByTenant();
        assertThat(domains).hasSize(2);
        assertThat(domains).filteredOn(d -> d.getIsPrimary() == 1)
                .singleElement()
                .satisfies(d -> assertThat(d.getDomain()).isEqualTo("p2.com"));

        // setPrimary 切换回第一个
        domainService.setPrimary(first.getId());
        assertThat(domainService.listByTenant())
                .filteredOn(d -> d.getIsPrimary() == 1)
                .singleElement()
                .satisfies(d -> assertThat(d.getId()).isEqualTo(first.getId()));
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void listByTenantIsIsolated() {
        Tenant a = createTenant("td-e");
        domainService.add("a1.com", false);
        domainService.add("a2.com", false);
        Tenant b = createTenant("td-f");
        domainService.add("b1.com", false);

        TenantContext.set(a);
        assertThat(domainService.listByTenant()).hasSize(2)
                .allSatisfy(d -> assertThat(d.getTenantId()).isEqualTo(a.getId()));
        TenantContext.set(b);
        assertThat(domainService.listByTenant()).hasSize(1)
                .allSatisfy(d -> assertThat(d.getTenantId()).isEqualTo(b.getId()));
    }

    @Test
    void writeOperationsRejectCrossTenantAccess() {
        Tenant a = createTenant("td-g");
        TenantDomain ownedByA = domainService.add("owned.com", false);
        Tenant b = createTenant("td-h");
        TenantContext.set(b);

        // 租户 B 不能设置 A 的域名为主域名(selectById 按上下文隔离 → 查不到 → 抛出)
        assertThatThrownBy(() -> domainService.setPrimary(ownedByA.getId()))
                .isInstanceOf(BizException.class);
        // 租户 B 不能改 A 域名状态
        assertThatThrownBy(() -> domainService.updateStatus(ownedByA.getId(), (short) 0))
                .isInstanceOf(BizException.class);
        // 租户 B 不能删 A 的域名
        assertThatThrownBy(() -> domainService.delete(ownedByA.getId()))
                .isInstanceOf(BizException.class);

        // 域名全局查询(bypass)仍可见;归属租户 A 上下文下 selectById 可见且未变更
        assertThat(domainService.findByDomain("owned.com")).isPresent();
        TenantContext.set(a);
        assertThat(domainService.selectById(ownedByA.getId()).orElseThrow().getStatus()).isEqualTo((short) 1);
    }

    @Test
    void deleteRemovesDomain() {
        Tenant a = createTenant("td-i");
        TenantDomain added = domainService.add("bye.com", false);

        domainService.delete(added.getId());

        assertThat(domainService.selectById(added.getId())).isEmpty();
        assertThat(domainService.findByDomain("bye.com")).isEmpty();
        // 删除后同名域名可重新注册(由另一租户注册)
        Tenant b = createTenant("td-j");
        assertThat(domainService.add("bye.com", false).getId()).isNotNull();
    }

    @Test
    void invalidDomainsAreRejected() {
        Tenant a = createTenant("td-k");
        List<String> invalid = List.of("", "   ", "not a domain!", "-leading.com",
                "trailing-.com", "a..b.com", "foo_bar.com", "a".repeat(256) + ".com");

        for (String bad : invalid) {
            assertThatThrownBy(() -> domainService.add(bad, false))
                    .as("domain=%s", bad)
                    .isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> domainService.add(null, false))
                .isInstanceOf(BizException.class);
    }

    @Test
    void updateStatusTogglesDomain() {
        Tenant a = createTenant("td-l");
        TenantDomain added = domainService.add("toggle.com", false);

        TenantDomain disabled = domainService.updateStatus(added.getId(), (short) 0);
        assertThat(disabled.getStatus()).isEqualTo((short) 0);
        assertThat(domainService.selectById(added.getId()).orElseThrow().getStatus()).isEqualTo((short) 0);
    }
}
