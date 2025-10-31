package com.tbelousov.tutube.service.rules;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.entity.UserAction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Базовый класс для всех триггерных правил.
 * <p>
 * Правило анализирует действие пользователя в контексте его истории
 * и может создать уведомление, если обнаружен паттерн.
 * </p>
 * Поддерживает:
 * <ul>
 *   <li>Cooldown - правило не сработает повторно в течение N часов</li>
 *   <li>Адаптацию тона под профиль пользователя</li>
 *   <li>Утилиты для работы со временем</li>
 * </ul>
 *
 * @see TriggerRule
 * @see RuleContext
 */
public abstract class AbstractTriggerRule implements TriggerRule {

    @Override
    public Optional<Notification> evaluate(UserAction action, RuleContext context) {
        if (!isApplicable(action, context))
            return Optional.empty();
        return evaluatePattern(action, context);
    }

    /**
     * Создаёт builder для уведомления с базовыми параметрами.
     *
     * @param userId ID пользователя
     * @param message текст уведомления
     * @param tone тон уведомления
     * @param delaySeconds задержка отправки (секунды)
     * @return builder для дальнейшей настройки
     */
    protected Notification.NotificationBuilder createNotification(
            Long userId, String message, User.ToneProfile tone, long delaySeconds) {
        return Notification.builder()
                .userId(userId)
                .message(message)
                .createdAt(Instant.now())
                .sendAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS))
                .sent(false)
                .tone(tone)
                .source("RULE")
                .triggerType(getRuleName());
    }

    /**
     * Проверяет, применимо ли правило к данному действию.
     *
     * @param action текущее действие
     * @param context контекст с историей пользователя
     * @return {@code true} если правило должно быть оценено
     */
    protected abstract boolean isApplicable(UserAction action, RuleContext context);

    /**
     * Оценивает паттерн поведения и создаёт уведомление при необходимости.
     *
     * @param action текущее действие
     * @param context контекст с историей пользователя
     * @return уведомление или {@link Optional#empty()}
     */
    protected abstract Optional<Notification> evaluatePattern(UserAction action, RuleContext context);

    /**
     * Выбирает текст сообщения в зависимости от тона пользователя.
     *
     * @param kindMessage текст для доброго тона
     * @param aggressiveMessage текст для пассивно-агрессивного тона
     * @param tone профиль тона пользователя
     * @return выбранный текст
     */
    protected String adaptTone(String kindMessage, String aggressiveMessage, User.ToneProfile tone) {
        return tone == User.ToneProfile.PASSIVE_AGGRESSIVE ? aggressiveMessage : kindMessage;
    }

    /**
     * Вычисляет момент времени в прошлом.
     *
     * @param amount количество единиц времени
     * @param unit единица времени
     * @return момент времени
     */
    protected Instant ago(long amount, ChronoUnit unit) {
        return Instant.now().minus(amount, unit);
    }
}