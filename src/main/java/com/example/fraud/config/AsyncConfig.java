package com.example.fraud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated pool for fire-and-forget work (audit writes) so it can never add
 * latency to, or fail, the request thread. If the queue fills up under extreme
 * load the task is dropped and logged rather than blocking the caller.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("audit-");
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setRejectedExecutionHandler((r, executor) ->
                log.warn("Audit executor saturated - dropping 1 audit record"));
        ex.initialize();
        return ex;
    }
}
