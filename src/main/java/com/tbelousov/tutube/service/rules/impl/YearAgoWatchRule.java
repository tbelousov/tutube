package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class YearAgoWatchRule extends AbstractTriggerRule {

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var zone = ZoneId.of(context.getUser().getTimezone());
        var target = action.getTimestamp().atZone(zone).toLocalDate();

        // Проверим, что год назад в этот же день смотрелось что-то похожее
        long oneYearAgoCount = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> action.getVideoTopic().equalsIgnoreCase(a.getVideoTopic()))
                .filter(a -> {
                    var date = a.getTimestamp().atZone(zone).toLocalDate();
                    return date.getDayOfMonth() == target.getDayOfMonth()
                            && date.getMonth() == target.getMonth()
                            && date.getYear() == target.getYear() - 1;
                })
                .count();

        if (oneYearAgoCount > 0) {
            var message = "Совпадение? Не думаю. Ровно год назад вы смотрели видео на эту же тему!";
            var aggressiveMessage = "Прошёл год, а ваши вкусы те же? Надо же как-то развиваться!";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(message, aggressiveMessage, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    86400 // через 1 день
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "YEAR_AGO_WATCH";
    }
}