package com.tbelousov.tutube.service;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.event.NotificationQueuedEvent;
import com.tbelousov.tutube.event.SimpleEventBus;
import com.tbelousov.tutube.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сервис управления очередью уведомлений.
 * <p>
 * Применяет throttling и сохраняет уведомления для последующей отправки
 * через {@link SmartNotificationDispatcher}.
 * </p>
 * @see NotificationThrottleService
 * @see SmartNotificationDispatcher
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepo;
    private final NotificationThrottleService throttle;
    private final MetricsService metrics;
    private final SimpleEventBus eventBus;

    /**
     * Ставит уведомление в очередь после проверки throttling-правил.
     * <p>
     * Если throttling блокирует уведомление, оно не сохраняется и логируется причина.
     * </p>
     * @param notification уведомление для постановки в очередь
     */
    public void queueNotification(Notification notification) {
        var decision = throttle.decide(
                notification.getUserId(),
                notification.getTriggerType(),
                notification.getSendAt() != null ? notification.getSendAt() : Instant.now());

        if (!decision.isAllowed()) {
            log.info("Throttle dropped notification for user {}: reason={}", notification.getUserId(), decision.getReason());
            metrics.recordNotificationThrottled(decision.getReason());
            return;
        }

        notification.setSendAt(decision.getFinalSendAt());
        var saved = notificationRepo.save(notification);

        metrics.recordNotificationCreated(notification.getSource(), notification.getTriggerType());

        eventBus.publish(new NotificationQueuedEvent(saved.getId()));

        log.info("Queued notification {} for user {} at {}", saved.getId(), saved.getUserId(), saved.getSendAt());
    }

    /**
     * Возвращает все уведомления в системе.
     *
     * @return список всех уведомлений
     */
    public Stream<Notification> streamAll() {
        return notificationRepo.streamAll();
    }

    /**
     * Возвращает уведомления конкретного пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список уведомлений пользователя
     */
    public List<Notification> getByUserId(Long userId) {
        return notificationRepo.findByUserId(userId);
    }
}