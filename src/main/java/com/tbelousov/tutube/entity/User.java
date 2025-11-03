package com.tbelousov.tutube.entity;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;

    private String name;

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private ToneProfile toneProfile = ToneProfile.KIND; // KIND, PASSIVE_AGGRESSIVE

    @Builder.Default
    private NotificationMode mode = NotificationMode.HYBRID; // RULES_ONLY, AI_ONLY, HYBRID

    public enum ToneProfile {
        KIND, PASSIVE_AGGRESSIVE
    }

    public enum NotificationMode {
        RULES_ONLY, AI_ONLY, HYBRID
    }
}