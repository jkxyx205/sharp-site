package com.rick.site.common.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行入口。
 *
 * <p>把耗时任务(Runnable)投递到 {@code translationExecutor} 线程池执行。
 * 调用方负责在任务体里捕获并设置租户上下文(显式传参,不隐式继承),例如:
 * <pre>{@code
 * Tenant tenant = TenantContext.require();
 * asyncRunner.run(() -> {
 *     TenantContext.set(tenant);
 *     try { service.syncToLanguages(...); } finally { TenantContext.clear(); }
 * });
 * }</pre>
 *
 * <p>任务体抛出的异常由 Spring 默认 {@code AsyncUncaughtExceptionHandler} 记录(WARN/ERROR),
 * 不再回流到调用方;调用方提交后应视为"已提交、可能稍后完成"。
 *
 * @author Rick.Xu
 */
@Component
public class AsyncRunner {

    /**
     * 异步执行 {@code task}。提交即返回,不等待;{@code task} 内的异常被异步线程吞并并记录日志。
     *
     * @param task 待异步执行的任务(由调用方负责租户上下文的 set/clear)
     */
    @Async("translationExecutor")
    public void run(Runnable task) {
        task.run();
    }
}
