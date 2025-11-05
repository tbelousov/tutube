package com.tbelousov.tutube.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.event.EventHandler;
import com.tbelousov.tutube.event.NotificationQueuedEvent;
import com.tbelousov.tutube.event.SimpleEventBus;
import com.tbelousov.tutube.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * Умный диспетчер уведомлений. Слушает {@link NotificationQueuedEvent}.
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
public class SmartNotificationDispatcher implements EventHandler<NotificationQueuedEvent> {
    private final NotificationRepository notificationRepo;
    private final SimpleEventBus eventBus;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            var t = new Thread(r, "notif-scheduler-");
            t.setDaemon(true);
            return t;
    });

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

    @PostConstruct
    void init() {
        eventBus.subscribe(this);
    }

    @Override
    public Class<NotificationQueuedEvent> eventType() {
        return NotificationQueuedEvent.class;
    }

    /**
     * Обрабатывает событие постановки уведомления в очередь.
     * <p>
     * Планирует индивидуальную задачу на время {@code sendAt}.
     * </p>
     * @param event событие с ID уведомления
     */
    @Override
    public void handle(NotificationQueuedEvent event) {
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
        long delayMs = Math.max(0, Duration.between(Instant.now(), notification.getSendAt()).toMillis()); // если 0, отправить немедленно

        log.debug("Scheduling notification {} for user {} in {} ms",
                notification.getId(), notification.getUserId(), delayMs);

        ScheduledFuture<?> task = scheduler.schedule(
                () -> sendNotification(notification.getId()),
                delayMs,
                TimeUnit.MILLISECONDS
        );

        scheduledTasks.asMap().compute(notification.getId(), (id, oldTask) -> {
            if (oldTask != null && !oldTask.isDone()) {
                oldTask.cancel(false);
            }
            return task;
        });
    }

    /**
     * Отправляет уведомление (имитация реальной отправки).
     *
     * @param notificationId ID уведомления
     */
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