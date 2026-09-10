package com.rick.site.tenant.context;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;

import java.util.Optional;

/**
 * 请求级租户上下文(TASK-0105)。
 *
 * <p>由 TenantFilter 在请求进入时 {@link #set}、请求结束(finally){@link #clear};
 * Service 层通过 {@link #require()} / {@link #requireTenantId()} 获取当前租户,
 * 禁止信任前端传入的 tenant_id(CLAUDE.md §4)。
 *
 * <p>线程模型:普通 ThreadLocal(非 Inheritable)。
 * 线程池复用场景必须由使用方保证 try/finally clear,防止租户污染;
 * 异步任务需要租户时显式传参,不隐式继承。
 */
public final class TenantContext {

    private static final ThreadLocal<Tenant> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Tenant tenant) {
        HOLDER.set(tenant);
    }

    public static Optional<Tenant> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static Tenant require() {
        return get().orElseThrow(() -> new BizException("当前请求未解析到租户"));
    }

    public static Long requireTenantId() {
        return require().getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
