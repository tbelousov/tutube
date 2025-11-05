package com.tbelousov.tutube.event;

import com.tbelousov.tutube.service.SmartNotificationDispatcher;

/**
 * Событие о постановке уведомления в очередь.
 * <p>
 * Обрабатывается {@link SmartNotificationDispatcher} для планирования отправки.
 * </p>
 * @param notificationId ID созданного уведомления
 */
public record NotificationQueuedEvent(Long notificationId) implements DomainEvent {}