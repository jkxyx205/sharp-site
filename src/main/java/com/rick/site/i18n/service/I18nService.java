package com.rick.site.i18n.service;

import java.util.Map;
import java.util.Optional;

/**
 * 统一语言查询服务(ARCHITECTURE.md §7 / TASK-0302 + TASK-0303)。
 *
 * <p>各业务 Service(product / article / page …)查询 i18n 内容时统一调用本服务:
 * 给定语言→内容的映射,按 {@code requestedLanguage} 取内容;
 * 该语言不存在时回退 {@code defaultLanguage}(TASK-0303),仍无则返回 empty。
 *
 * <p>避免在每个 Service 重复实现 fallback 逻辑。
 *
 * @author Rick.Xu
 */
public interface I18nService {

    /**
     * 按语言解析内容,带默认语言回退。
     *
     * @param byLanguage       语言→内容映射(仅包含实际存在的内容,值非 null)
     * @param requestedLanguage 请求语言(如来自 {@link com.rick.site.i18n.context.LocaleContext})
     * @param defaultLanguage  租户默认语言(如来自 {@code Tenant.defaultLanguage})
     * @param <T>              内容类型
     * @return 命中的内容;requested 与 default 均不存在时返回 empty
     */
    <T> Optional<T> resolve(Map<String, ? extends T> byLanguage,
                            String requestedLanguage, String defaultLanguage);
}
