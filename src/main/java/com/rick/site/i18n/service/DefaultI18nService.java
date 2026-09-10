package com.rick.site.i18n.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 默认 I18nService 实现(TASK-0302 / TASK-0303)。
 *
 * <p>fallback 顺序:请求语言 → 租户默认语言 → empty。
 *
 * @author Rick.Xu
 */
@Component
public class DefaultI18nService implements I18nService {

    @Override
    public <T> Optional<T> resolve(Map<String, ? extends T> byLanguage,
                                   String requestedLanguage, String defaultLanguage) {
        if (byLanguage == null || byLanguage.isEmpty()) {
            return Optional.empty();
        }
        if (requestedLanguage != null) {
            T value = byLanguage.get(requestedLanguage);
            if (value != null) {
                return Optional.of(value);
            }
        }
        if (defaultLanguage != null && !defaultLanguage.equals(requestedLanguage)) {
            T value = byLanguage.get(defaultLanguage);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
