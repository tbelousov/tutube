package com.tbelousov.tutube.mapper;

import com.tbelousov.tutube.dto.CreateActionRequest;
import com.tbelousov.tutube.entity.UserAction;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ActionMapper {

    public UserAction toEntity(CreateActionRequest dto) {
        return UserAction.builder()
                .userId(dto.userId())
                .actionType(dto.actionType())
                .timestamp(dto.timestamp() != null ? dto.timestamp() : Instant.now())
                .deviceType(dto.deviceType())
                .location(dto.location())
                .weather(dto.weather())
                .videoId(dto.videoId())
                .channelId(dto.channelId())
                .commentId(dto.commentId())
                .donationAmount(dto.donationAmount())
                .videoProgress(dto.videoProgress())
                .videoTopic(dto.videoTopic())
                .seeksCount(dto.seeksCount())
                .watchDurationSec(dto.watchDurationSec())
                .introSkipSec(dto.introSkipSec())
                .typosCount(dto.typosCount())
                .sentimentScore(dto.sentimentScore())
                .build();
    }
}