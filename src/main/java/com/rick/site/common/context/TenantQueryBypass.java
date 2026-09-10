package com.rick.site.common.context;

import java.util.function.Supplier;

/**
 * 租户查询过滤的临时旁路标记(CLAUDE.md §4)。
 *
 * <p>{@link com.rick.site.common.config.SiteDatabaseConfig} 会在 TenantContext 存在时
 * 自动向单表租户表查询追加 {@code tenant_id} 过滤。少数查询需要跨租户全局语义
 * (如域名全局唯一校验 {@code findByDomain}),此时用 {@link #supply} 包裹该查询,
 * 临时跳过 tenant_id 过滤(逻辑删除 is_deleted 过滤仍由框架保留)。
 *
 * <p>线程模型:普通 ThreadLocal,调用方负责 try/finally 清理(Supply 已内置);
 * 不向线程池复用场景泄漏(非 Inheritable)。
 *
 * @author Rick.Xu
 */
public final class TenantQueryBypass {

    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();

    public static boolean isBypassed() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    /**
     * 在跳过租户过滤的上下文中执行一次查询动作,结束后自动清理标记。
     * 仅供需要跨租户全局语义的查询(域名唯一校验)使用,业务隔离查询勿用。
     */
    public static <T> T supply(Supplier<T> action) {
        BYPASS.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            BYPASS.remove();
        }
    }

    private TenantQueryBypass() {
    }
}
