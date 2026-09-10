package com.rick.site.i18n.service;

import com.rick.site.i18n.model.LocaleResolution;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Locale 解析器(TASK-0301)。
 *
 * <p>统一处理 URL 与语言的映射(REQUIREMENTS.md §7):
 * <ul>
 *   <li>默认语言:无前缀 {@code /} / {@code /about}</li>
 *   <li>其他语言:前缀 {@code /zh-cn/} / {@code /zh-cn/about}</li>
 * </ul>
 * 默认语言来自 {@code Tenant.defaultLanguage}。
 *
 * @author Rick.Xu
 */
public interface LocaleResolver {

    /**
     * 解析当前请求的语言与有效路径。
     *
     * @param request 当前 HTTP 请求
     * @return 解析结果(语言标签 + 去前缀后的有效路径),无租户时回退平台默认语言
     */
    LocaleResolution resolve(HttpServletRequest request);
}
