package com.tbelousov.tutube.service;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

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
    private final ApplicationEventPublisher events;

    /**
     * Событие о постановке уведомления в очередь.
     * <p>
     * Обрабатывается {@link SmartNotificationDispatcher} для планирования отправки.
     * </p>
     * @param notificationId ID созданного уведомления
     */
    public record NotificationQueuedEvent(Long notificationId) {}

    /**
     * Ставит уведомление в очередь после проверки throttling-правил.
     * <p>
     * Если throttling блокирует уведомление, оно не сохраняется и логируется причина.
     * </p>
     * @param notification уведомление для постановки в очередь
     */
    @Transactional
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

        events.publishEvent(new NotificationQueuedEvent(saved.getId()));

        log.info("Queued notification {} for user {} at {}", saved.getId(), saved.getUserId(), saved.getSendAt());
    }

    /**
     * Возвращает все уведомления в системе.
     *
     * @return список всех уведомлений
     */
    public List<Notification> getAll() {
        return notificationRepo.findAll();
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