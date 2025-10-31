package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MeteoSensitivityRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 30;
    private static final int MIN_WEATHERS_PER_DAY = 2; // 2 погоды = 1 смена
    private static final int MIN_DAYS_WITH_PATTERN = 3;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context)
                && action.getVideoTopic() != null && action.getVideoTopic().toLowerCase().contains("relax")
                && action.getWeather() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var zone = ZoneId.of(context.getUser().getTimezone());
        var since = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);

        // Группируем действия по дням и собираем погоду для каждого дня
        Map<LocalDate, List<String>> weatherByDay = context.getRecentActions().stream()
                .filter(a -> a.getTimestamp().isAfter(since))
                .filter(a -> a.getWeather() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getTimestamp().atZone(zone).toLocalDate(),
                        Collectors.mapping(UserAction::getWeather, Collectors.toList())
                ));

        // Ищем дни, где была смена погоды И просмотр relax-видео
        long daysWithPattern = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> a.getTimestamp().isAfter(since))
                .filter(a -> a.getVideoTopic() != null && a.getVideoTopic().toLowerCase().contains("relax"))
                .map(a -> a.getTimestamp().atZone(zone).toLocalDate())
                .distinct()
                .filter(day -> {
                    List<String> weathers = weatherByDay.get(day);
                    // В этот день была смена погоды (минимум 2 разные погоды)
                    return weathers != null && weathers.stream().distinct().count() >= MIN_WEATHERS_PER_DAY;
                })
                .count();

        if (daysWithPattern >= MIN_DAYS_WITH_PATTERN) {
            var message = "Погода меняется, а вы настроены на расслабление! Отличный выбор для этого времени.";
            var aggressiveMessage = "Погодка шалит. Врубаем расслабон и не грустим.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(message, aggressiveMessage, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800 // через 30 минут
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "METEO_SENSITIVITY"; }
}