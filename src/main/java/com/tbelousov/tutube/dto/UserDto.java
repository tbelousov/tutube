package com.tbelousov.tutube.dto;

import com.tbelousov.tutube.entity.User;

public record UserDto(
        Long id, String name, String timezone, User.ToneProfile toneProfile, User.NotificationMode mode
) {}