package com.tbelousov.tutube.controller;

import com.tbelousov.tutube.dto.NotificationDto;
import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.mapper.NotificationMapper;
import com.tbelousov.tutube.service.NotificationService;
import com.tbelousov.tutube.service.SmartNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.data.domain.Sort.Direction.DESC;

/**
 * REST API для получения уведомлений.
 * <p>
 * Уведомления создаются автоматически системой на основе анализа поведения пользователей.
 * </p>
 * @see NotificationService
 * @see SmartNotificationDispatcher
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    /**
     * Возвращает все уведомления в системе (для отладки).
     *
     * @return список всех уведомлений
     */
    @GetMapping
    public List<NotificationDto> getAll() {
        return notificationService.getAll().stream().map(notificationMapper::toDto).toList();
    }

    /**
     * Возвращает уведомления конкретного пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список уведомлений пользователя (отправленных и ожидающих)
     */
    @GetMapping("/user/{userId}")
    public List<NotificationDto> getByUser(@PathVariable Long userId) {
        return notificationService.getByUserId(userId).stream().map(notificationMapper::toDto).toList();
    }
}