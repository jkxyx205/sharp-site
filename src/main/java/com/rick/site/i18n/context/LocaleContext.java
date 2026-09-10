package com.rick.site.i18n.context;

import com.rick.site.i18n.model.LocaleResolution;

import java.util.Optional;

/**
 * 请求级语言上下文(TASK-0301)。
 *
 * <p>由 {@code LocaleFilter} 在请求进入时 {@link #set}、请求结束(finally){@link #clear};
 * Service 层通过 {@link #requireLanguage()} 获取当前语言,做 i18n 查询与 fallback。
 *
 * <p>线程模型同 {@link com.rick.site.tenant.context.TenantContext}:普通 ThreadLocal,
 * 线程池复用场景须由使用方保证 try/finally clear。
 *
 * @author Rick.Xu
 */
public final class LocaleContext {

    private static final ThreadLocal<LocaleResolution> HOLDER = new ThreadLocal<>();

    private LocaleContext() {
    }

    public static void set(LocaleResolution resolution) {
        HOLDER.set(resolution);
    }

    public static Optional<LocaleResolution> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static String requireLanguage() {
        return get().map(LocaleResolution::language)
                .orElseThrow(() -> new IllegalStateException("当前请求未解析到语言"));
    }

    public static void clear() {
        HOLDER.remove();
    }
}
