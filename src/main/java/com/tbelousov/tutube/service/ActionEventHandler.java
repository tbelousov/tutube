package com.tbelousov.tutube.service;

import com.tbelousov.tutube.config.TutubeProperties;
import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.exception.UserActionNotFoundException;
import com.tbelousov.tutube.exception.UserNotFoundException;
import com.tbelousov.tutube.repository.NotificationRepository;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.repository.UserRepository;
import com.tbelousov.tutube.service.ai.AiNotificationService;
import com.tbelousov.tutube.service.rules.RuleContext;
import com.tbelousov.tutube.service.rules.TriggerRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Обработчик событий действий пользователей.
 * <p>
 * Слушает {@link ActionService.ActionSavedEvent} и запускает асинхронный анализ:
 * <ul>
 *   <li>В режиме {@code RULES_ONLY} или {@code HYBRID} - прогоняет через 26 эвристических правил</li>
 *   <li>В режиме {@code AI_ONLY} или {@code HYBRID} - с вероятностью вызывает ИИ-генерацию</li>
 * </ul>
 *
 * @see TriggerRule
 * @see AiNotificationService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActionEventHandler {

    private final UserRepository userRepo;
    private final UserActionRepository actionRepo;
    private final NotificationRepository notificationRepo;
    private final NotificationService notificationService;
    private final AiNotificationService aiNotificationService;
    private final MetricsService metrics;
    private final List<TriggerRule> triggerRules;
    private final TutubeProperties properties;

    /**
     * Обрабатывает сохранённое действие после коммита транзакции.
     * <p>
     * Выполняется асинхронно в пуле {@code notificationTaskExecutor}.
     * </p>
     * @param event событие о сохранении действия
     * @throws UserNotFoundException если пользователь не найден
     * @throws UserActionNotFoundException если действие не найдено
     */
    @Async("notificationTaskExecutor")
    @EventListener
    public void onActionSaved(ActionService.ActionSavedEvent event) {
        var user = userRepo.findById(event.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + event.userId()));
        var action = actionRepo.findById(event.actionId())
                .orElseThrow(() -> new UserActionNotFoundException("UserAction not found: " + event.actionId()));

        var ruleContext = buildRuleContext(user);
        var mode = user.getMode();

        if (mode == User.NotificationMode.RULES_ONLY || mode == User.NotificationMode.HYBRID) {
            evaluateRules(action, ruleContext);
        }

        if ((mode == User.NotificationMode.AI_ONLY || mode == User.NotificationMode.HYBRID) && shouldTriggerAi()) {
            evaluateAi(user, action, ruleContext);
        }
    }

    /**
     * Прогоняет действие через все зарегистрированные правила.
     *
     * @param action текущее действие
     * @param context контекст с историей пользователя
     */
    private void evaluateRules(UserAction action, RuleContext context) {
        triggerRules.forEach(rule -> {
            try {
                var result = rule.evaluate(action, context);
                metrics.recordRuleEvaluation(rule.getRuleName(), result.isPresent());
                result.ifPresent(notification -> {
                    notificationService.queueNotification(notification);
                    log.info("Rule '{}' triggered notification for user {}", rule.getRuleName(), action.getUserId());
                });
            } catch (Exception e) {
                log.error("Error evaluating rule {} for user {}: {}", rule.getRuleName(), action.getUserId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Вызывает ИИ-генерацию уведомления на основе паттернов поведения.
     *
     * @param user пользователь
     * @param action текущее действие
     * @param context контекст правил
     */
    private void evaluateAi(User user, UserAction action, RuleContext context) {
        try {
            var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            var recentActions = actionRepo.findByUserIdAndTimestampAfter(user.getId(), thirtyDaysAgo);
            aiNotificationService.generateNotification(user, action, recentActions, context)
                    .ifPresent(notificationService::queueNotification);
        } catch (Exception e) {
            log.error("Error generating AI notification for user {}: {}", user.getId(), e.getMessage(), e);
        }
    }

    /**
     * Строит контекст для оценки правил.
     * <p>
     * Загружает историю действий за последний год и недавно сработавшие триггеры.
     * </p>
     * @param user пользователь
     * @return контекст с историей и информацией о cooldown
     */
    private RuleContext buildRuleContext(User user) {
        var oneYearAgo = Instant.now().minus(366, ChronoUnit.DAYS);
        var recentActions = actionRepo.findByUserIdAndTimestampAfter(user.getId(), oneYearAgo);

        var cooldownHours = properties.getNotifications().getCooldownHours();
        var cooldownStart = Instant.now().minus(cooldownHours, ChronoUnit.HOURS);

        Set<String> recentTriggers = notificationRepo
                .findByUserIdAndCreatedAtAfter(user.getId(), cooldownStart).stream()
                .map(Notification::getTriggerType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return RuleContext.builder()
                .user(user)
                .recentActions(recentActions)
                .recentlyTriggeredRules(recentTriggers)
                .build();
    }

    /**
     * Определяет, следует ли вызывать ИИ на основе вероятности из конфигурации.
     *
     * @return {@code true} если ИИ должен быть вызван
     */
    private boolean shouldTriggerAi() {
        return Math.random() < properties.getAi().getTriggerProbability();
    }
}