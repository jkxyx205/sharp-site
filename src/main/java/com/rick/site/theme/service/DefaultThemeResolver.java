package com.rick.site.theme.service;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 默认主题解析器:直接返回 {@link Tenant#getThemeId()}。
 *
 * <p>主题模板存在性由 Thymeleaf 渲染期校验(modern 主题随代码发布);
 * 未配置 themeId 视为配置错误,抛 BizException。
 *
 * @author Rick.Xu
 */
@Component
public class DefaultThemeResolver implements ThemeResolver {

    @Override
    public String resolveTheme(Tenant tenant) {
        if (tenant == null) {
            throw new BizException("租户未解析,无法确定主题");
        }
        String themeId = tenant.getThemeId();
        if (StringUtils.isBlank(themeId)) {
            throw new BizException("租户主题未配置: " + tenant.getCode());
        }
        return themeId;
    }
}
