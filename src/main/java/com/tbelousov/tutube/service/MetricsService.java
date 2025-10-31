package com.tbelousov.tutube.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsService {
    private final MeterRegistry meterRegistry;

    public void recordNotificationCreated(String source, String triggerType) {
        Counter.builder("tutube.notifications.created")
                .tag("source", source)
                .tag("trigger", triggerType)
                .register(meterRegistry)
                .increment();
    }

    public void recordNotificationThrottled(String reason) {
        Counter.builder("tutube.notifications.throttled")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordAiCallDuration(long durationMs, boolean success) {
        Timer.builder("tutube.ai.call.duration")
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordRuleEvaluation(String ruleName, boolean triggered) {
        Counter.builder("tutube.rules.evaluations")
                .tag("rule", ruleName)
                .tag("triggered", String.valueOf(triggered))
                .register(meterRegistry)
                .increment();
    }
}