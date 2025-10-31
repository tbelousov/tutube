package com.tbelousov.tutube.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ToneProfile toneProfile = ToneProfile.KIND; // KIND, PASSIVE_AGGRESSIVE

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationMode mode = NotificationMode.HYBRID; // RULES_ONLY, AI_ONLY, HYBRID

    public enum ToneProfile {
        KIND, PASSIVE_AGGRESSIVE
    }

    public enum NotificationMode {
        RULES_ONLY, AI_ONLY, HYBRID
    }
}