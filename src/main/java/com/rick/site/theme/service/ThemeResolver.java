package com.rick.site.theme.service;

import com.rick.site.tenant.entity.Tenant;

/**
 * 主题解析器(ARCHITECTURE.md §5)。
 *
 * <p>Controller 不得根据 tenantId 判断模板,只能通过本接口由 Tenant 解析 themeId,
 * 再拼接模板路径 {@code themes/{themeId}/{page}.html}。
 *
 * @author Rick.Xu
 */
public interface ThemeResolver {

    /**
     * 解析租户当前主题。
     *
     * @param tenant 当前请求租户(来自 TenantContext)
     * @return themeId,如 modern / industrial
     */
    String resolveTheme(Tenant tenant);
}
