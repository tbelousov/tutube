package com.tbelousov.tutube.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {
    private final TutubeProperties props;

    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notif-");
        executor.setCorePoolSize(props.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(props.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(props.getAsync().getQueueCapacity());
        executor.initialize();
        return executor;
    }
}