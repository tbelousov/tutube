package com.tbelousov.tutube.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AiNotificationResponse(
        @NotNull String message,

        @JsonProperty("send_delay_seconds")
        @PositiveOrZero
        Integer sendDelaySeconds,

        String reasoning // почему AI выбрал это сообщение
){}