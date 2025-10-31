package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NightOwlRule extends AbstractTriggerRule {

    private static final int NIGHT_END_HOUR = 5;
    private static final int MIN_DISTINCT_NIGHTS = 3;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.COMMENT && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        int hour = action.getTimestamp().atZone(ZoneId.of(context.getUser().getTimezone())).getHour();
        long distinctNights = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT)
                .filter(a -> a.getTimestamp().isAfter(action.getTimestamp().minus(3, ChronoUnit.DAYS)))
                .filter(a -> {
                    int h = a.getTimestamp().atZone(ZoneId.of(context.getUser().getTimezone())).getHour();
                    return h <= NIGHT_END_HOUR;
                })
                .map(a -> a.getTimestamp().atZone(ZoneId.of(context.getUser().getTimezone())).toLocalDate())
                .distinct()
                .count();

        if (hour <= NIGHT_END_HOUR && distinctNights >= MIN_DISTINCT_NIGHTS) {
            var kind = "🦉 Кажется, вы сова! Ни ночи без комментария. Берегите себя!";
            var aggressive = "Опять ночь, опять комменты. Спать вообще собираетесь? Вот вам видео про бессонницу.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    3600
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "NIGHT_OWL";
    }
}