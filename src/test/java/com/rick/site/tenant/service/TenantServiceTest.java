package com.rick.site.tenant.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0101 验收测试:Tenant 实体 / DAO / Service。
 * 事务自动回滚,不在库中留测试数据。
 */
@SpringBootTest
@Transactional
class TenantServiceTest {

    @Autowired
    private TenantService tenantService;

    @Test
    void saveCreatesTenantWithDefaults() {
        Tenant saved = tenantService.save(Tenant.builder()
                .code("acme")
                .name("Acme Corp")
                .themeId("modern")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDefaultLanguage()).isEqualTo("en-US");
        assertThat(saved.getStatus()).isEqualTo((short) 1);
        assertThat(saved.getBaseEntityInfo().getCreateTime()).isNotNull();
        assertThat(saved.getBaseEntityInfo().getUpdateTime()).isNotNull();
        assertThat(saved.getBaseEntityInfo().getCreateBy()).isEqualTo(1L);
        assertThat(saved.getBaseEntityInfo().getDeleted()).isFalse();

        Optional<Tenant> byCode = tenantService.findByCode("acme");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getName()).isEqualTo("Acme Corp");
        assertThat(byCode.get().getThemeId()).isEqualTo("modern");

        assertThat(tenantService.findById(saved.getId())).isPresent();
    }

    @Test
    void duplicateCodeIsRejected() {
        tenantService.save(Tenant.builder().code("dup").name("A").themeId("modern").build());

        assertThatThrownBy(() -> tenantService.save(
                Tenant.builder().code("dup").name("B").themeId("industrial").build()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void saveUpdatesExistingTenant() {
        Tenant saved = tenantService.save(Tenant.builder()
                .code("upd").name("Old Name").themeId("modern").build());

        saved.setName("New Name");
        saved.setThemeId("industrial");
        tenantService.save(saved);

        Tenant reloaded = tenantService.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New Name");
        assertThat(reloaded.getThemeId()).isEqualTo("industrial");
        assertThat(reloaded.getBaseEntityInfo().getCreateTime()).isEqualTo(saved.getBaseEntityInfo().getCreateTime());
        assertThat(reloaded.getCode()).isEqualTo("upd");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(tenantService.findById(-1L)).isEmpty();
    }
}
