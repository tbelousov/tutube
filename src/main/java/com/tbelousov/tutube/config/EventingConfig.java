package com.tbelousov.tutube.config;

import com.tbelousov.tutube.event.SimpleEventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EventingConfig {

    private final TutubeProperties props;

    @Bean
    public SimpleEventBus eventBus() {
        var pool = props.getAsync();
        return new SimpleEventBus(pool.getQueueCapacity(), pool.getCorePoolSize(), "evt-");
    }
}