package com.tbelousov.tutube.dto;

import com.tbelousov.tutube.dto.validator.ValidActionRequest;
import com.tbelousov.tutube.entity.UserAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

@ValidActionRequest
public record CreateActionRequest(
        @NotNull Long userId,
        @NotNull UserAction.ActionType actionType,
        Instant timestamp, // если null - server-side Instant.now()
        UserAction.DeviceType deviceType,

        Long videoId,
        Long channelId,
        Long commentId,

        @PositiveOrZero Double donationAmount,
        @Min(0) @Max(100) Integer videoProgress,
        String videoTopic,
        @PositiveOrZero Integer seeksCount,
        @PositiveOrZero Integer watchDurationSec,
        @PositiveOrZero Integer introSkipSec,
        @PositiveOrZero Integer typosCount,
        @Min(-100) @Max(100) Integer sentimentScore,

        String location,
        String weather
) {}