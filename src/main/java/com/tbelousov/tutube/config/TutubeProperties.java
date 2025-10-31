package com.tbelousov.tutube.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "tutube")
@Data
@Validated
public class TutubeProperties {
    @Valid
    private AiConfig ai = new AiConfig();
    @Valid
    private NotificationConfig notifications = new NotificationConfig();
    @Valid
    private ThrottleConfig throttle = new ThrottleConfig();
    @Valid
    private AsyncPool async = new AsyncPool();

    @Data
    @Validated
    public static class AiConfig {
        private boolean enabled = true;
        @NotBlank(message = "AI provider must not be blank")
        private String provider = "openai";
        private String apiKey;
        @NotBlank
        private String apiUrl = "https://api.openai.com/v1/chat/completions";
        @NotBlank
        private String model = "gpt-4o-mini";
        @Min(0) @Max(1)
        private double triggerProbability = .2;
    }

    @Data
    @Validated
    public static class NotificationConfig {
        @Positive
        private int cooldownHours = 24;
    }

    @Data
    @Validated
    public static class ThrottleConfig {
        @NotNull
        private Duration minInterval = Duration.ofMinutes(10);
        @Positive
        private int maxPerHour = 5;
        @Positive
        private int maxPerDay = 20;
        @Positive
        private int maxPendingPerUser = 50;
        @NotNull
        private Duration deduplicateWindow = Duration.ofHours(24);
    }

    @Data
    @Validated
    public static class AsyncPool {
        @Positive
        private int corePoolSize = 4;
        @Positive
        private int maxPoolSize = 8;
        @Positive
        private int queueCapacity = 1000;
    }
}