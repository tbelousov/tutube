package com.tbelousov.tutube.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "actions", indexes = {
        @Index(name = "idx_user_timestamp", columnList = "userId,timestamp"),
        @Index(name = "idx_user_action_type", columnList = "userId,actionType"),
        @Index(name = "idx_channel_timestamp", columnList = "channelId,timestamp"),
        @Index(name = "idx_video_timestamp", columnList = "videoId,timestamp")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    private String location; // город или координаты
    private String weather; // sunny, rainy, cold, etc.

    // Дополнительные данные специфичные для действий
    private Long videoId;
    private Long channelId;
    private Long commentId;
    @PositiveOrZero
    private Double donationAmount;
    @Min(0) @Max(100)
    private Integer videoProgress; // %
    private String videoTopic; // java, cooking, travel, etc.
    @PositiveOrZero
    private Integer seeksCount;        // число перемоток за сессию просмотра
    @PositiveOrZero
    private Integer watchDurationSec;  // длительность текущей сессии
    @PositiveOrZero
    private Integer introSkipSec;      // сколько секунд интро скипнули (если известно)
    @PositiveOrZero
    private Integer typosCount;        // для COMMENT: число «опечаток/ошибок» в тексте
    @Min(-100) @Max(100)
    private Integer sentimentScore;    // для COMMENT: -100..100 (негатив..позитив)

    public enum ActionType {
        @JsonProperty("VIEW_VIDEO") VIEW_VIDEO,
        @JsonProperty("LIKE_VIDEO") LIKE_VIDEO,
        @JsonProperty("LIKE_COMMENT") LIKE_COMMENT,
        @JsonProperty("COMMENT") COMMENT,
        @JsonProperty("DONATE") DONATE,
        @JsonProperty("SUBSCRIBE") SUBSCRIBE,
        @JsonProperty("ENABLE_NOTIFICATIONS") ENABLE_NOTIFICATIONS
    }

    public enum DeviceType {
        @JsonProperty("MOBILE") MOBILE,
        @JsonProperty("DESKTOP") DESKTOP
    }

    @Override
    public String toString() {
        return "UserAction{" +
                "actionType=" + actionType +
                ", timestamp=" + timestamp +
                (deviceType != null ? ", deviceType=" + deviceType : "") +
                (location != null ? ", location='" + location + '\'' : "") +
                (weather != null ? ", weather='" + weather + '\'' : "") +
                (videoId != null ? ", videoId=" + videoId : "") +
                (channelId != null ? ", channelId=" + channelId : "") +
                (commentId != null ? ", commentId=" + commentId : "") +
                (donationAmount != null ? ", donationAmount=" + donationAmount : "") +
                (videoProgress != null ? ", videoProgress=" + videoProgress : "") +
                (videoTopic != null ? ", videoTopic='" + videoTopic + '\'' : "") +
                (seeksCount != null ? ", seeksCount=" + seeksCount : "") +
                (watchDurationSec != null ? ", watchDurationSec=" + watchDurationSec : "") +
                (introSkipSec != null ? ", introSkipSec=" + introSkipSec : "") +
                (typosCount != null ? ", typosCount=" + typosCount : "") +
                (sentimentScore != null ? ", sentimentScore=" + sentimentScore : "") +
                '}';
    }
}