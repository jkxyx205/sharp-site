package com.rick.site.theme.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0201 验收测试:ThemeResolver 由 Tenant.themeId 解析主题,不按 tenantId 判断。
 * 纯单元测试。
 */
class ThemeResolverTest {

    private final ThemeResolver themeResolver = new DefaultThemeResolver();

    @Test
    void resolvesThemeIdFromTenant() {
        Tenant t = Tenant.builder().code("acme").name("Acme").themeId("modern").build();
        assertThat(themeResolver.resolveTheme(t)).isEqualTo("modern");

        Tenant industrial = Tenant.builder().code("ind").name("Ind").themeId("industrial").build();
        assertThat(themeResolver.resolveTheme(industrial)).isEqualTo("industrial");
    }

    @Test
    void blankThemeIdThrows() {
        Tenant t = Tenant.builder().code("acme").name("Acme").themeId(" ").build();
        assertThatThrownBy(() -> themeResolver.resolveTheme(t))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("acme");
    }

    @Test
    void nullTenantThrows() {
        assertThatThrownBy(() -> themeResolver.resolveTheme(null))
                .isInstanceOf(BizException.class);
    }
}
