package com.tbelousov.tutube.service;

import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class ActionServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5")
            .withDatabaseName("tutube_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("tutube.ai.enabled", () -> "false"); // отключаем AI в тестах
    }

    @Autowired
    private ActionService actionService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private NotificationRepository notificationRepo;

    @Test
    void weatherBasedRule_shouldTriggerOnRainyWeather() {
        // Given
        var user = userRepo.save(User.builder()
                .name("Test User")
                .timezone("UTC")
                .toneProfile(User.ToneProfile.KIND)
                .mode(User.NotificationMode.RULES_ONLY)
                .build());

        // When
        var action = UserAction.builder()
                .userId(user.getId())
                .actionType(UserAction.ActionType.VIEW_VIDEO)
                .timestamp(Instant.now())
                .deviceType(UserAction.DeviceType.MOBILE)
                .weather("rainy")
                .videoId(1L)
                .channelId(1L)
                .videoProgress(50)
                .videoTopic("java")
                .build();

        actionService.registerAction(action);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var notifications = notificationRepo.findByUserId(user.getId());
                    assertThat(notifications).isNotEmpty();
                    assertThat(notifications).anyMatch(n ->
                            n.getTriggerType().equals("WEATHER_BASED") &&
                                    n.getMessage().contains("дождь")
                    );
                });
    }

    @Test
    void nightOwlRule_shouldTriggerAfterThreeNightComments() {
        // Given
        var user = userRepo.save(User.builder()
                .name("Night Owl")
                .timezone("UTC")
                .toneProfile(User.ToneProfile.PASSIVE_AGGRESSIVE)
                .mode(User.NotificationMode.RULES_ONLY)
                .build());

        // When: 3 night comments
        for (int i = 0; i < 3; i++) {
            var nightComment = UserAction.builder()
                    .userId(user.getId())
                    .actionType(UserAction.ActionType.COMMENT)
                    .timestamp(Instant.now().minus(i, ChronoUnit.DAYS)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .withHour(2)
                            .toInstant())
                    .deviceType(UserAction.DeviceType.MOBILE)
                    .videoId((long) i)
                    .channelId(1L)
                    .commentId((long) i)
                    .build();
            actionService.registerAction(nightComment);
        }

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var notifications = notificationRepo.findByUserId(user.getId());
                    assertThat(notifications).anyMatch(n -> n.getTriggerType().equals("NIGHT_OWL"));
                });
    }
}