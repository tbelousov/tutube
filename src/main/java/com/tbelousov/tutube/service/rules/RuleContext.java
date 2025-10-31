package com.tbelousov.tutube.service.rules;

import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.entity.UserAction;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Контекст для оценки правил.
 * <p>
 * Содержит всю необходимую информацию для анализа паттернов поведения:
 * <ul>
 *   <li>Профиль пользователя</li>
 *   <li>История последних действий</li>
 *   <li>Список недавно сработавших правил (для cooldown)</li>
 * </ul>
 * </p>
 */
@Data
@Builder
public class RuleContext {
    /** Пользователь */
    private User user;
    /** История последних действий */
    private List<UserAction> recentActions;
    /** Типы триггеров, сработавших в cooldown-периоде */
    private Set<String> recentlyTriggeredRules;

    /**
     * Проверяет, срабатывало ли правило недавно (в пределах cooldown).
     *
     * @param ruleName имя правила
     * @return {@code true} если правило в cooldown
     */
    public boolean wasRecentlyTriggered(String ruleName) {
        return recentlyTriggeredRules != null &&
                recentlyTriggeredRules.contains(ruleName);
    }
}