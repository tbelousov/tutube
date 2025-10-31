package com.tbelousov.tutube.dto;

import com.tbelousov.tutube.entity.User;

import java.time.Instant;

public record NotificationDto(
        Long id, Long userId, String message, Instant createdAt,
        Instant sendAt, boolean sent, String triggerType, String source, User.ToneProfile tone
) {}