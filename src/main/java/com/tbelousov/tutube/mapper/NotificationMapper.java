package com.tbelousov.tutube.mapper;

import com.tbelousov.tutube.dto.NotificationDto;
import com.tbelousov.tutube.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getUserId(),
                entity.getMessage(),
                entity.getCreatedAt(),
                entity.getSendAt(),
                entity.isSent(),
                entity.getTriggerType(),
                entity.getSource(),
                entity.getTone()
        );
    }
}