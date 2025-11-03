package com.tbelousov.tutube.entity;

import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private Long id;

    private Long userId;

    private String message;

    private Instant createdAt;

    @Setter
    private Instant sendAt;

    @Builder.Default
    private boolean sent = false;

    private String triggerType;

    private User.ToneProfile tone;

    private String source; // RULE, AI

    private String context;
}