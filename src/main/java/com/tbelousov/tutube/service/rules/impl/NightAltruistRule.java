package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

@Component
public class NightAltruistRule extends AbstractTriggerRule {

    private static final int NIGHT_BEGIN_HOUR = 2;
    private static final int NIGHT_END_HOUR = 5;
    private static final int HOUR_FOR_NOTIFICATION = 9; // отправим утром в 9:00 локального времени пользователя

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.DONATE && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var zone = ZoneId.of(context.getUser().getTimezone());
        int hour = action.getTimestamp().atZone(zone).getHour();

        if (hour >= NIGHT_BEGIN_HOUR && hour <= NIGHT_END_HOUR) {
            var nextMorning = action.getTimestamp().atZone(zone).withHour(HOUR_FOR_NOTIFICATION).withMinute(0).withSecond(0);
            if (!nextMorning.isAfter(action.getTimestamp().atZone(zone))) {
                nextMorning = nextMorning.plusDays(1);
            }
            long delaySec = Duration.between(action.getTimestamp(), nextMorning.toInstant()).getSeconds();
            delaySec = Math.max(0, delaySec);

            var kind = "Ночью приходит вдохновение на щедрость. Спасибо, что поддерживаете авторов в тихий час!";
            var aggressive = "Ночные донаты - это интересно. Утром помните об этом решении? 😉";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    delaySec
            ).context("nightDonation=true").build());
        }
        return Optional.empty();
    }

    @Override public String getRuleName() { return "NIGHT_ALTRUIST"; }
}