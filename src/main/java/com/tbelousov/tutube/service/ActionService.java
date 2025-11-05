package com.tbelousov.tutube.service;

import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.event.ActionSavedEvent;
import com.tbelousov.tutube.event.SimpleEventBus;
import com.tbelousov.tutube.exception.UserNotFoundException;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Принимает пользовательские действия и публикует доменные события.
 *
 * @see ActionEventHandler
 * @see ActionSavedEvent
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActionService {
    private final UserActionRepository actionRepo;
    private final UserRepository userRepo;
    private final SimpleEventBus eventBus;

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

        eventBus.publish(new ActionSavedEvent(user.getId(), saved.getId()));

        log.info("Registered action: {} for user {}", action.getActionType(), action.getUserId());

        return saved.getId();
    }
}