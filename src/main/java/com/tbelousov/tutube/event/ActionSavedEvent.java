package com.tbelousov.tutube.event;

import com.tbelousov.tutube.service.ActionEventHandler;

/**
 * Событие о сохранении действия пользователя.
 * <p>
 * Обрабатывается в {@link ActionEventHandler}.
 * </p>
 * @param userId ID пользователя
 * @param actionId ID созданного действия
 */
public record ActionSavedEvent(Long userId, Long actionId) implements DomainEvent {}