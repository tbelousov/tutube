package com.tbelousov.tutube.service.rules;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;

import java.util.Optional;

/**
 * Интерфейс триггерного правила.
 * Каждое правило анализирует действие пользователя и может создать уведомление.
 */
public interface TriggerRule {
    /**
     * Оценивает правило для данного действия.
     *
     * @param action текущее действие
     * @param context контекст с историей и информацией о cooldown
     * @return уведомление или {@link Optional#empty()}
     */
    Optional<Notification> evaluate(UserAction action, RuleContext context);

    /**
     * Возвращает уникальное имя правила для дедупликации.
     *
     * @return имя правила (например, "NIGHT_OWL")
     */
    String getRuleName();

    /**
     * Проверяет, может ли правило сработать (не в cooldown).
     *
     * @param context контекст с информацией о недавно сработавших правилах
     * @return {@code true} если правило может сработать
     */
    default boolean canTrigger(RuleContext context) {
        return !context.wasRecentlyTriggered(getRuleName());
    }
}