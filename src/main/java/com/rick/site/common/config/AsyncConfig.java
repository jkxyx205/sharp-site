package com.rick.site.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置:启用 {@code @Async},提供翻译同步专用线程池。
 *
 * <p>{@code translationExecutor} 专供内容多语言翻译同步({@code syncToLanguages})使用——
 * 该步骤逐语种调用 LLM,单次耗时数秒到数十秒,放请求线程会阻塞后台保存。
 *
 * <p>租户上下文不隐式继承:由调用方在提交前捕获 {@link com.rick.site.tenant.context.TenantContext},
 * 在异步任务体里 {@code set}/{@code clear}(见 AsyncRunner 用法),避免线程池复用导致的租户污染。
 *
 * @author Rick.Xu
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "translationExecutor")
    public ThreadPoolTaskExecutor translationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("translation-");
        // 队列满时退化为调用方同步执行(后台保存请求线程),不丢任务
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}
