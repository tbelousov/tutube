package com.tbelousov.tutube.controller;

import com.tbelousov.tutube.dto.ActionCreatedResponse;
import com.tbelousov.tutube.dto.CreateActionRequest;
import com.tbelousov.tutube.dto.validator.ValidActionRequest;
import com.tbelousov.tutube.exception.UserNotFoundException;
import com.tbelousov.tutube.mapper.ActionMapper;
import com.tbelousov.tutube.service.ActionEventHandler;
import com.tbelousov.tutube.service.ActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * REST API для регистрации действий пользователей на платформе Tutube.
 * <p>
 * Каждое зарегистрированное действие асинхронно анализируется системой триггеров
 * для возможной генерации персонализированного уведомления.
 * </p>
 * @see ActionService
 * @see ActionEventHandler
 */
@RestController
@RequestMapping("/actions")
@RequiredArgsConstructor
public class ActionController {
    private final ActionService actionService;
    private final ActionMapper actionMapper;

    /**
     * Регистрирует новое действие пользователя.
     * <p>
     * После сохранения действие попадает в систему триггеров через event-driven механизм.
     * </p>
     * @param request данные действия с валидацией через {@link ValidActionRequest}
     * @return ID созданного действия и HTTP 201 Created
     * @throws UserNotFoundException если пользователь не найден
     */
    @PostMapping
    public ResponseEntity<ActionCreatedResponse> addAction(@Valid @RequestBody CreateActionRequest request) {
        var action = actionMapper.toEntity(request);
        var id = actionService.registerAction(action);
        return ResponseEntity
                .created(URI.create("/actions/" + id))
                .body(new ActionCreatedResponse(id));
    }
}