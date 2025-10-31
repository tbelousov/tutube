package com.tbelousov.tutube.mapper;

import com.tbelousov.tutube.dto.UserDto;
import com.tbelousov.tutube.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User entity) {
        return new UserDto(
                entity.getId(),
                entity.getName(),
                entity.getTimezone(),
                entity.getToneProfile(),
                entity.getMode()
        );
    }

    public User toEntity(UserDto dto) {
        return User.builder()
                .id(dto.id())
                .name(dto.name())
                .timezone(dto.timezone())
                .toneProfile(dto.toneProfile() != null ? dto.toneProfile() : User.ToneProfile.KIND)
                .mode(dto.mode() != null ? dto.mode() : User.NotificationMode.HYBRID)
                .build();
    }
}