package com.tbelousov.tutube.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbelousov.tutube.config.TutubeProperties;
import com.tbelousov.tutube.dto.AiNotificationResponse;
import com.tbelousov.tutube.dto.OpenAiResponse;
import com.tbelousov.tutube.dto.UserContext;
import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.exception.InvalidAIResponseException;
import com.tbelousov.tutube.service.MetricsService;
import com.tbelousov.tutube.service.rules.RuleContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Интеграция с LLM-генератором текста (по умолчанию OpenAI в тестовом режиме).
 * Оборачивается Circuit Breaker. При недоступности - падение в fallback-сообщение.
 * На выходе формирует доменную сущность {@link Notification}.
 *
 * @see AiNotificationResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiNotificationService {
    private final MetricsService metrics;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TutubeProperties properties;

    /**
     * Генерирует уведомление через OpenAI API.
     * <p>
     * Защищено Circuit Breaker и Retry (resilience4j).
     * </p>
     * @param user пользователь
     * @param currentAction текущее действие
     * @param recent история действий за последний месяц
     * @param context контекст правил
     * @return уведомление или {@link Optional#empty()} если AI отключен
     * @throws InvalidAIResponseException если ответ AI некорректен
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "generateFallbackNotification")
    @Retry(name = "aiService")
    public Optional<Notification> generateNotification(User user, UserAction currentAction, List<UserAction> recent, RuleContext context) {
        if (!properties.getAi().isEnabled() || properties.getAi().getApiKey() == null) {
            log.debug("AI is disabled or API key not configured");
            return Optional.empty();
        }

        var userContext = buildUserContext(user, currentAction, context);
        var aiResponse = callOpenAI(userContext, recent, user.getToneProfile());

        if (aiResponse != null && aiResponse.message() != null) {
            return Optional.of(Notification.builder()
                    .userId(user.getId())
                    .message(aiResponse.message())
                    .createdAt(Instant.now())
                    .sendAt(Instant.now().plus(
                            aiResponse.sendDelaySeconds() != null ?
                                    aiResponse.sendDelaySeconds() : 3600,
                            ChronoUnit.SECONDS))
                    .sent(false)
                    .tone(user.getToneProfile())
                    .source("AI")
                    .triggerType("AI_GENERATED")
                    .context(aiResponse.reasoning())
                    .build());
        }

        return Optional.empty();
    }

    /**
     * Fallback при недоступности AI.
     *
     * @return generic-уведомление с пометкой AI_FALLBACK
     */
    private Optional<Notification> generateFallbackNotification(User user, UserAction currentAction,
            List<UserAction> recent, RuleContext context, Exception ex) {
        log.warn("AI service unavailable, using fallback. Reason: {}", ex.getMessage());
        return Optional.of(createFallbackNotification(user));
    }

    /**
     * Строит контекст пользователя для промпта.
     *
     * @return объект с паттернами поведения
     */
    private UserContext buildUserContext(User user, UserAction currentAction, RuleContext context) {
        var patterns = new HashMap<String, Object>();
        var actions = context.getRecentActions(); // 1 year

        // Топ темы
        Map<String, Long> topicCounts = actions.stream()
                .filter(a -> a.getVideoTopic() != null)
                .collect(Collectors.groupingBy(UserAction::getVideoTopic, Collectors.counting()));
        patterns.put("favorite_topics", topicCounts);

        // Активность по часам
        Map<Integer, Long> hourActivity = actions.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getTimestamp().atZone(java.time.ZoneId.of(user.getTimezone())).getHour(),
                        Collectors.counting()));
        patterns.put("active_hours", hourActivity);

        // Статистика
        patterns.put("total_actions_1y", actions.size());
        patterns.put("donations_count", actions.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.DONATE).count());
        patterns.put("comments_count", actions.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT).count());
        patterns.put("current_action", currentAction.getActionType());
        patterns.put("current_device", currentAction.getDeviceType());

        return UserContext.builder()
                .userId(user.getId())
                .behaviorPatterns(patterns)
                .currentLocation(currentAction.getLocation())
                .currentWeather(currentAction.getWeather())
                .preferredTone(user.getToneProfile().toString())
                .build();
    }

    /**
     * Вызывает OpenAI API для генерации уведомления.
     *
     * @return ответ с текстом уведомления и задержкой отправки
     * @throws InvalidAIResponseException если ответ некорректен
     */
    private AiNotificationResponse callOpenAI(UserContext context, List<UserAction> recent, User.ToneProfile tone) {
        var toneDescription = tone == User.ToneProfile.PASSIVE_AGGRESSIVE ?
                "пассивно-агрессивный и саркастичный (как сова Duolingo)" : "добрый и дружелюбный";

        var prompt = String.format(
                """
                        Ты креативный генератор уведомлений для видеоплатформы Tutube. Сгенерируй ОДНО неожиданное, но релевантное уведомление на русском языке.
                        
                        Контекст юзера:
                        %s
                        
                        Действия юзера за последний месяц:
                        %s
                        
                        Обязательные условия:
                        1. Тон: %s
                        2. Будь креативным и удивительным
                        3. Сошлись на конкретную закономерность поведения
                        4. Сообщение меньше 200 символов
                        
                        Отвечай СТРОГО ТОЛЬКО в JSON: {"message": "text", "send_delay_seconds": number, "reasoning": "why"}
                        где send_delay_seconds: 60-86400""",
                context.toPromptString(),
                recent,
                toneDescription
        );

        var requestBody = Map.of(
                "model", properties.getAi().getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", .9,
                "max_tokens", 300
        );

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getAi().getApiKey());

        var entity = new HttpEntity<Map<String, Object>>(requestBody, headers);

        long start = System.currentTimeMillis();
        ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
                properties.getAi().getApiUrl(), HttpMethod.POST, entity, OpenAiResponse.class);

        metrics.recordAiCallDuration(System.currentTimeMillis() - start, response.getStatusCode() == HttpStatus.OK);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            var body = response.getBody();
            if (body.getChoices() != null && !body.getChoices().isEmpty()) {
                var content = body.getChoices().get(0).getMessage().getContent();
                try {
                    return objectMapper.readValue(content, AiNotificationResponse.class);
                } catch (JsonProcessingException e) {
                    throw new InvalidAIResponseException("Invalid AI response: " + content);
                }
            }
        }

        throw new InvalidAIResponseException("Invalid AI response");
    }

    /**
     * Создаёт fallback-уведомление при недоступности AI.
     */
    private Notification createFallbackNotification(User user) {
        var message = user.getToneProfile() == User.ToneProfile.PASSIVE_AGGRESSIVE ?
                "Вы опять здесь? Ладно, держите что-нибудь интересное." :
                "Рады видеть вас снова! Есть что-то новенькое для вас 😊";

        return Notification.builder()
                .userId(user.getId())
                .message(message)
                .createdAt(Instant.now())
                .sendAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .sent(false)
                .tone(user.getToneProfile())
                .source("AI_FALLBACK")
                .triggerType("AI_GENERATED")
                .context("Fallback - AI unavailable")
                .build();
    }
}