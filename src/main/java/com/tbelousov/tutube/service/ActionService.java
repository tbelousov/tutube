package com.tbelousov.tutube.service;

import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.exception.UserNotFoundException;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Принимает пользовательские действия и публикует доменные события после коммита транзакции.
 * Инварианты:
 * <ul>
 *   <li>Пользователь должен существовать;</li>
 *   <li>Действие сохраняется атомарно;</li>
 *   <li>Событие {@link ActionSavedEvent} публикуется только после успешного коммита.</li>
 * </ul>
 *
 * @see ActionEventHandler
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {
    private final UserActionRepository actionRepo;
    private final UserRepository userRepo;
    private final ApplicationEventPublisher events;

    /**
     * Событие о сохранении действия пользователя.
     * <p>
     * Обрабатывается в {@link ActionEventHandler#onActionSaved} после коммита транзакции.
     * </p>
     * @param userId ID пользователя
     * @param actionId ID созданного действия
     */
    public record ActionSavedEvent(Long userId, Long actionId) {}

    /**
     * Регистрирует действие пользователя и публикует событие для системы триггеров.
     *
     * @param action данные действия
     * @return ID сохранённого действия
     * @throws UserNotFoundException если пользователь не найден
     */
    public Long registerAction(UserAction action) {
        var user = userRepo.findById(action.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + action.getUserId()));

        var saved = actionRepo.save(action);

        events.publishEvent(new ActionSavedEvent(user.getId(), saved.getId()));

        log.info("Registered action: {} for user {}", action.getActionType(), action.getUserId());

        return saved.getId();
    }
}