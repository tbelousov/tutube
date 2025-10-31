package com.tbelousov.tutube.service;

import com.tbelousov.tutube.config.TutubeProperties;
import com.tbelousov.tutube.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Сервис throttling для защиты от спама уведомлениями.
 * <p>
 * Применяет множественные ограничения:
 * <ul>
 *   <li>Максимум X уведомлений в час/день</li>
 *   <li>Минимальный интервал между отправками</li>
 *   <li>Дедупликация по типу триггера</li>
 *   <li>Лимит очереди ожидающих уведомлений</li>
 * </ul>
 *
 * @see TutubeProperties.ThrottleConfig
 */
@Service
@RequiredArgsConstructor
public class NotificationThrottleService {

    private final NotificationRepository repo;
    private final TutubeProperties props;

    /**
     * Принимает решение о допустимости отправки уведомления.
     *
     * @param userId ID пользователя
     * @param triggerType тип триггера (для дедупликации)
     * @param desiredSendAt желаемое время отправки
     * @return решение с окончательным временем отправки или причиной блокировки
     */
    public ThrottleDecision decide(Long userId, String triggerType, Instant desiredSendAt) {
        Instant now = Instant.now();

        // Суточный/часовой лимит по отправленным
        long perHour = repo.countSentSince(userId, now.minus(1, ChronoUnit.HOURS));
        if (perHour >= props.getThrottle().getMaxPerHour()) {
            return ThrottleDecision.blocked("hourly-cap");
        }

        long perDay = repo.countSentSince(userId, now.minus(1, ChronoUnit.DAYS));
        if (perDay >= props.getThrottle().getMaxPerDay()) {
            return ThrottleDecision.blocked("daily-cap");
        }

        // Дедуп по типу триггера
        if (triggerType != null && repo.existsTriggerSince(
                userId, triggerType, now.minus(props.getThrottle().getDeduplicateWindow()))) {
            return ThrottleDecision.blocked("duplicate-trigger");
        }

        // Ограничение очереди
        int pending = repo.countPendingByUser(userId);
        if (pending >= props.getThrottle().getMaxPendingPerUser()) {
            return ThrottleDecision.blocked("too-many-pending");
        }

        // Минимальный интервал между доставками
        var lastDelivered = repo.findLastDeliveryTime(userId);
        var earliest = lastDelivered == null
                ? now
                : lastDelivered.plus(props.getThrottle().getMinInterval());

        var finalSendAt = desiredSendAt.isBefore(earliest) ? earliest : desiredSendAt;

        return ThrottleDecision.allowed(finalSendAt);
    }

    /**
     * Результат проверки throttling.
     * <p>{@code allowed} разрешена ли отправка</p>
     * <p>{@code finalSendAt} окончательное время отправки (с учётом минимального интервала)</p>
     * <p>{@code reason} причина блокировки (если {@code allowed = false})</p>
     */
    @Getter
    @AllArgsConstructor(staticName = "of")
    public static class ThrottleDecision {
        private final boolean allowed;
        private final Instant finalSendAt;
        private final String reason;

        public static ThrottleDecision allowed(Instant when) { return of(true, when, null); }
        public static ThrottleDecision blocked(String reason) { return of(false, null, reason); }
    }
}