package com.rick.site.tenant.service;

import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.dto.TenantConfigView;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.entity.TenantConfig;
import com.rick.site.tenant.entity.TenantConfigI18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-0103 验收测试:企业固定信息 base 行 upsert(每租户一行、tenantId 防伪造)+
 * company_name/company_name_short/address/copyright 按语种 i18n 幂等 upsert 与展示解析。
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
    void firstSaveCreatesConfigBaseRow() {
        Tenant a = createTenant("tc-a");
        TenantConfig saved = configService.save(TenantConfig.builder()
                .email("info@acme.com")
                .phone("+1-555-0100")
                .icp("沪ICP备0000号")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(a.getId());
        assertThat(saved.getBaseEntityInfo().getCreateTime()).isNotNull();
        assertThat(saved.getBaseEntityInfo().getCreateBy()).isEqualTo(1L);

        TenantConfig loaded = configService.findByTenant().orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("info@acme.com");
        assertThat(loaded.getPhone()).isEqualTo("+1-555-0100");
    }

    @Test
    void secondSaveUpdatesSameBaseRow() {
        Tenant a = createTenant("tc-b");
        TenantConfig first = configService.save(TenantConfig.builder()
                .email("old@acme.com").phone("111").build());
        TenantConfig second = configService.save(TenantConfig.builder()
                .email("new@acme.com").phone("222").build());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getBaseEntityInfo().getCreateTime()).isEqualTo(first.getBaseEntityInfo().getCreateTime());
        // 全列更新:未提交字段被清空是预期语义
        TenantConfig loaded = configService.findByTenant().orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("new@acme.com");
        assertThat(loaded.getPhone()).isEqualTo("222");
    }

    @Test
    void configIsIsolatedPerTenant() {
        Tenant a = createTenant("tc-c");
        configService.save(TenantConfig.builder().email("a@acme.com").build());
        Tenant b = createTenant("tc-d");
        configService.save(TenantConfig.builder().email("b@acme.com").build());

        TenantContext.set(a);
        assertThat(configService.findByTenant().orElseThrow().getEmail()).isEqualTo("a@acme.com");
        TenantContext.set(b);
        assertThat(configService.findByTenant().orElseThrow().getEmail()).isEqualTo("b@acme.com");
    }

    @Test
    void entityTenantIdIsOverriddenByContextValue() {
        Tenant a = createTenant("tc-e");
        Tenant b = createTenant("tc-f");
        // 上下文为 A,实体上伪造 tenantId=B;框架注入覆盖,以 A 为准
        TenantContext.set(a);
        TenantConfig saved = configService.save(TenantConfig.builder()
                .tenantId(b.getId())
                .email("spoofed@acme.com")
                .build());

        assertThat(saved.getTenantId()).isEqualTo(a.getId());
        TenantContext.set(b);
        assertThat(configService.findByTenant()).isEmpty();
        TenantContext.set(a);
        assertThat(configService.findByTenant()).isPresent();
    }

    @Test
    void findByTenantReturnsEmptyWhenAbsent() {
        createTenant("tc-g");
        assertThat(configService.findByTenant()).isEmpty();
    }

    @Test
    void saveI18nIsIdempotentPerLanguage() {
        Tenant a = createTenant("tc-h");
        Long configId = configService.save(TenantConfig.builder().email("info@acme.com").build()).getId();

        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("en-US").companyName("Acme Corp")
                .companyNameShort("Acme").address("1 Main St")
                .copyright("© 2026 Acme Corp").build());
        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("en-US").companyName("Acme Corp Ltd")
                .companyNameShort("ACL").build());

        Map<String, TenantConfigI18n> map = configService.loadI18nMap(configId);
        assertThat(map).hasSize(1);
        assertThat(map.get("en-US").getCompanyName()).isEqualTo("Acme Corp Ltd");
        assertThat(map.get("en-US").getCompanyNameShort()).isEqualTo("ACL");
        // 未提交字段被清空是全列更新的预期语义
        assertThat(map.get("en-US").getAddress()).isNull();
    }

    @Test
    void saveI18nStoresMultipleLanguages() {
        Tenant a = createTenant("tc-i");
        Long configId = configService.save(TenantConfig.builder().email("info@acme.com").build()).getId();

        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("en-US").companyName("Acme Corp").build());
        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("zh-CN").companyName("阿克姆公司").build());

        Map<String, TenantConfigI18n> map = configService.loadI18nMap(configId);
        assertThat(map).hasSize(2);
        assertThat(map.get("en-US").getCompanyName()).isEqualTo("Acme Corp");
        assertThat(map.get("zh-CN").getCompanyName()).isEqualTo("阿克姆公司");
    }

    @Test
    void resolveForDisplayHitsCurrentLanguageThenFallsBack() {
        Tenant a = createTenant("tc-j");
        Long configId = configService.save(TenantConfig.builder()
                .email("info@acme.com").icp("沪ICP备0000号").build()).getId();
        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("en-US").companyName("Acme Corp")
                .address("1 Main St").copyright("© 2026 Acme Corp").build());
        configService.saveI18n(configId, TenantConfigI18n.builder()
                .language("zh-CN").companyName("阿克姆公司")
                .address("南京路1号").copyright("© 2026 阿克姆公司").build());

        // 命中当前语种
        TenantConfigView zh = configService.resolveForDisplay("zh-CN", "en-US").orElseThrow();
        assertThat(zh.companyName()).isEqualTo("阿克姆公司");
        assertThat(zh.address()).isEqualTo("南京路1号");
        assertThat(zh.email()).isEqualTo("info@acme.com");
        assertThat(zh.icp()).isEqualTo("沪ICP备0000号");

        // 当前语种缺失 → 回退默认语种 en-US
        TenantConfigView fallback = configService.resolveForDisplay("fr-FR", "en-US").orElseThrow();
        assertThat(fallback.companyName()).isEqualTo("Acme Corp");
        assertThat(fallback.address()).isEqualTo("1 Main St");
    }

    @Test
    void resolveForDisplayReturnsEmptyWhenNoBaseRow() {
        createTenant("tc-k");
        assertThat(configService.resolveForDisplay("en-US", "en-US")).isEmpty();
    }
}
