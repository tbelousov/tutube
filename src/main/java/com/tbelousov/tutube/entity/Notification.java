package com.tbelousov.tutube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_user_sent", columnList = "userId,sent"),
        @Index(name = "idx_send_at", columnList = "sendAt,sent"),
        @Index(name = "idx_user_trigger_created", columnList = "userId,triggerType,createdAt")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 1000, nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    @Setter
    private Instant sendAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean sent = false;

    @Column(nullable = false)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.ToneProfile tone;

    @Column(nullable = false)
    private String source; // RULE, AI

    @Column(length = 2000)
    private String context;
}