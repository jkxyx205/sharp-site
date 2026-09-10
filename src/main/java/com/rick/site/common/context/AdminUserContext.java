package com.rick.site.common.context;

import java.util.Optional;

/**
 * 后台登录用户上下文(Phase 9 接入 Spring Security 后由认证过滤器填充)。
 *
 * <p>当前阶段未接入认证,上下文为空 → {@code SiteDatabaseConfig.getUserId()} 回退 1L
 * (系统用户,与参考实现一致)。{@link #clear()} 必须在请求结束 finally 调用,
 * 防止线程池复用残留用户。
 *
 * @author Rick.Xu
 */
public final class AdminUserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private AdminUserContext() {
    }

    public static void setUserId(Long id) {
        HOLDER.set(id);
    }

    public static Optional<Long> getUserId() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
