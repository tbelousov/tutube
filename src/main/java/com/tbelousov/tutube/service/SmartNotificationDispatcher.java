package com.tbelousov.tutube.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

/**
 * Умный диспетчер уведомлений.
 * <p>
 * Работает по принципу: при создании уведомления сразу планируется индивидуальная задача
 * на момент {@code sendAt} через {@link ScheduledExecutorService}.
 * <p>
 * При старте приложения восстанавливает незавершённые задачи из базы.
 *
 * @see NotificationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartNotificationDispatcher {
    private final NotificationRepository notificationRepo;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final Cache<Long, ScheduledFuture<?>> scheduledTasks = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(7)) // автоматическая очистка
            .maximumSize(10_000) // защита от переполнения
            .removalListener((key, value, cause) -> {
                if (value != null && !((ScheduledFuture<?>) value).isDone()) {
                    log.debug("Evicted task for notification {}, reason: {}", key, cause);
                    ((ScheduledFuture<?>) value).cancel(false);
                }
            })
            .build();

    /**
     * Обрабатывает событие постановки уведомления в очередь.
     * <p>
     * Планирует индивидуальную задачу на время {@code sendAt}.
     * </p>
     * @param event событие с ID уведомления
     */
    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationQueued(NotificationService.NotificationQueuedEvent event) {
        var notification = notificationRepo.findById(event.notificationId()).orElse(null);

        if (notification != null && !notification.isSent()) {
            scheduleNotification(notification);
            log.debug("Scheduled notification {} after transaction commit", event.notificationId());
        }
    }

    /**
     * Планирует отправку уведомления на конкретное время.
     * Индивидуальная задача для каждого уведомления.
     *
     * @param notification уведомление для планирования
     */
    public void scheduleNotification(Notification notification) {
        long delaySeconds = notification.getSendAt().getEpochSecond() - Instant.now().getEpochSecond();

        if (delaySeconds < 0) {
            delaySeconds = 0; // отправить немедленно
        }

        log.debug("Scheduling notification {} for user {} in {} seconds",
                notification.getId(), notification.getUserId(), delaySeconds);

        ScheduledFuture<?> task = scheduler.schedule(
                () -> sendNotification(notification.getId()),
                delaySeconds,
                TimeUnit.SECONDS
        );

        var old = scheduledTasks.asMap().put(notification.getId(), task);
        if (old != null && !old.isDone()) {
            old.cancel(false);
        }
    }

    /**
     * Отправляет уведомление (имитация реальной отправки).
     *
     * @param notificationId ID уведомления
     */
    @Transactional
    protected void sendNotification(Long notificationId) {
        try {
            int updated = notificationRepo.markSentIfDue(notificationId, Instant.now());
            if (updated == 1) {
                // делаем вид, что куда-то отправляем
                log.info("📨 SENDING notification {}", notificationId);
                scheduledTasks.invalidate(notificationId);
            } else {
                // уже отправлено или ещё не время
                scheduledTasks.invalidate(notificationId);
            }
        } catch (Exception e) {
            log.error("Error sending notification {}: {}", notificationId, e.getMessage(), e);
            scheduledTasks.invalidate(notificationId);
        }
    }

    /**
     * Восстанавливает запланированные уведомления при старте приложения.
     * Просроченные уведомления отправляются немедленно, остальные перепланируются.
     * В реальном проекте надо заменить на что-то более надёжное.
     */
    @PostConstruct
    public void restoreScheduledNotifications() {
        log.info("Restoring scheduled notifications...");
        try {
            List<Notification> pending = notificationRepo.findBySentFalse();

            int restored = 0;
            int immediate = 0;

            for (var n : pending) {
                if (n.getSendAt().isAfter(Instant.now())) {
                    scheduleNotification(n);
                    restored++;
                } else {
                    // Просроченные отправляем сразу
                    sendNotification(n.getId());
                    immediate++;
                }
            }

            log.info("Restored {} pending notifications ({} scheduled, {} sent immediately)", pending.size(), restored, immediate);
        } catch (Exception e) {
            log.error("Error restoring scheduled notifications: {}", e.getMessage(), e);
        }
    }

    /**
     * Корректное завершение работы при остановке приложения.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down notification dispatcher...");
        scheduledTasks.asMap().values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        scheduledTasks.invalidateAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}