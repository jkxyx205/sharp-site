package com.rick.site.tenant.service;

import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0104 验收测试:Host → Tenant 解析。
 * 对应 TASKS.md:a.example.com → tenant A,b.example.com → tenant B。
 * 解析阶段无 TenantContext(由 TenantFilter 在解析后写入),事务自动回滚。
 */
@SpringBootTest
@Transactional
class TenantResolverTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    @Autowired
    private TenantResolver tenantResolver;

    private Tenant tenantA;
    private Tenant tenantB;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @BeforeEach
    void seed() {
        tenantA = tenantService.save(Tenant.builder()
                .code("company-a").name("Company A").themeId("modern").build());
        tenantB = tenantService.save(Tenant.builder()
                .code("company-b").name("Company B").themeId("industrial").build());
        // 域名写入需上下文(tenant_id 由 TenantContext 注入)
        TenantContext.set(tenantA);
        domainService.add("a.example.com", true);
        TenantContext.set(tenantB);
        domainService.add("b.example.com", true);
        // 解析阶段(TenantFilter 前)无上下文,保持与生产一致
        TenantContext.clear();
    }

    @Test
    void resolvesTenantByHost() {
        assertThat(tenantResolver.resolveByHost("a.example.com"))
                .get().extracting(Tenant::getId).isEqualTo(tenantA.getId());
        assertThat(tenantResolver.resolveByHost("b.example.com"))
                .get().extracting(Tenant::getId).isEqualTo(tenantB.getId());
    }

    @Test
    void hostIsCaseInsensitiveAndPortIsStripped() {
        assertThat(tenantResolver.resolveByHost("A.Example.COM"))
                .get().extracting(Tenant::getId).isEqualTo(tenantA.getId());
        assertThat(tenantResolver.resolveByHost("a.example.com:8080"))
                .get().extracting(Tenant::getId).isEqualTo(tenantA.getId());
        assertThat(tenantResolver.resolveByHost("  b.example.com:443  "))
                .get().extracting(Tenant::getId).isEqualTo(tenantB.getId());
    }

    @Test
    void unknownHostResolvesToEmpty() {
        assertThat(tenantResolver.resolveByHost("unknown.example.com")).isEmpty();
        assertThat(tenantResolver.resolveByHost(null)).isEmpty();
        assertThat(tenantResolver.resolveByHost("   ")).isEmpty();
    }

    @Test
    void malformedHostResolvesToEmptyInsteadOfThrowing() {
        assertThat(tenantResolver.resolveByHost("not a host!")).isEmpty();
        assertThat(tenantResolver.resolveByHost("[::1]:8080")).isEmpty();
        assertThat(tenantResolver.resolveByHost("-bad.example.com")).isEmpty();
    }

    @Test
    void disabledDomainDoesNotResolve() {
        TenantDomain domain = domainService.findByDomain("a.example.com").orElseThrow();
        TenantContext.set(tenantA);
        domainService.updateStatus(domain.getId(), (short) 0);
        TenantContext.clear();

        assertThat(tenantResolver.resolveByHost("a.example.com")).isEmpty();
        // 其他租户不受影响
        assertThat(tenantResolver.resolveByHost("b.example.com")).isPresent();
    }

    @Test
    void disabledTenantDoesNotResolve() {
        Optional<Tenant> before = tenantResolver.resolveByHost("a.example.com");
        assertThat(before).isPresent();

        tenantA.setStatus((short) 0);
        tenantService.save(tenantA);

        assertThat(tenantResolver.resolveByHost("a.example.com")).isEmpty();
    }
}
