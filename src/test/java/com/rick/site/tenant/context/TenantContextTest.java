package com.rick.site.tenant.context;

import com.rick.common.http.exception.BizException;
import com.rick.site.tenant.entity.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0105 验收测试:TenantContext 设置/获取/清理 + 线程复用安全。
 * 纯单元测试,不依赖 Spring 上下文与数据库。
 */
class TenantContextTest {

    private static final Tenant TENANT_A = Tenant.builder().id(100L).code("a").name("A").themeId("modern").build();
    private static final Tenant TENANT_B = Tenant.builder().id(200L).code("b").name("B").themeId("industrial").build();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setGetClear() {
        assertThat(TenantContext.get()).isEmpty();

        TenantContext.set(TENANT_A);
        assertThat(TenantContext.get()).contains(TENANT_A);
        assertThat(TenantContext.require()).isEqualTo(TENANT_A);
        assertThat(TenantContext.requireTenantId()).isEqualTo(100L);

        TenantContext.clear();
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void requireThrowsWhenContextEmpty() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(BizException.class);
        assertThatThrownBy(TenantContext::requireTenantId)
                .isInstanceOf(BizException.class);
    }

    @Test
    void laterSetOverridesPrevious() {
        TenantContext.set(TENANT_A);
        TenantContext.set(TENANT_B);
        assertThat(TenantContext.requireTenantId()).isEqualTo(200L);
    }

    /** 模拟 Web 容器线程池复用:上一个请求 clear 后,同线程下一个请求看不到残留租户 */
    @Test
    void threadPoolReuseDoesNotLeakTenant() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 第一个"请求":设置后按 try/finally 模式清理
            pool.submit(() -> {
                try {
                    TenantContext.set(TENANT_A);
                } finally {
                    TenantContext.clear();
                }
            }).get(5, TimeUnit.SECONDS);

            // 同一线程处理第二个"请求":上下文必须为空
            Boolean leaked = pool.submit(() -> TenantContext.get().isPresent()).get(5, TimeUnit.SECONDS);
            assertThat(leaked).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    /** 业务抛异常时,只要遵循 try/finally 模式,上下文仍被清理 */
    @Test
    void clearRunsEvenWhenBusinessThrows() {
        try {
            TenantContext.set(TENANT_A);
            throw new IllegalStateException("boom");
        } catch (IllegalStateException ignored) {
            // 模拟过滤器捕获后继续
        } finally {
            TenantContext.clear();
        }
        assertThat(TenantContext.get()).isEmpty();
    }

    /** 两个线程并发使用各自的租户,互不可见 */
    @Test
    void concurrentThreadsSeeOnlyTheirOwnTenant() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothSet = new CountDownLatch(2);
        CountDownLatch bothRead = new CountDownLatch(2);
        try {
            Future<Long> fa = pool.submit(() -> runInThread(TENANT_A, bothSet, bothRead));
            Future<Long> fb = pool.submit(() -> runInThread(TENANT_B, bothSet, bothRead));

            assertThat(fa.get(10, TimeUnit.SECONDS)).isEqualTo(100L);
            assertThat(fb.get(10, TimeUnit.SECONDS)).isEqualTo(200L);
        } finally {
            pool.shutdownNow();
        }
    }

    private Long runInThread(Tenant tenant, CountDownLatch bothSet, CountDownLatch bothRead) throws InterruptedException {
        try {
            TenantContext.set(tenant);
            bothSet.countDown();
            // 等两个线程都完成 set 后再读,制造真实交叠
            bothSet.await(5, TimeUnit.SECONDS);
            Long seen = TenantContext.get().map(Tenant::getId).orElse(null);
            bothRead.countDown();
            bothRead.await(5, TimeUnit.SECONDS);
            return seen;
        } finally {
            TenantContext.clear();
        }
    }
}
