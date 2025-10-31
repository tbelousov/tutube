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
public class UnfinishedVideoRule extends AbstractTriggerRule {

    private static final int VIDEO_DONE_PERCENT = 90;
    private static final int EVENING_BEGIN_HOUR = 18;
    private static final int EVENING_END_HOUR = 22;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Проверяем: видео не досмотрено и сейчас вечер
        if (action.getVideoProgress() != null && action.getVideoProgress() < VIDEO_DONE_PERCENT) {
            int hour = action.getTimestamp().atZone(ZoneId.of(context.getUser().getTimezone())).getHour();

            if (hour >= EVENING_BEGIN_HOUR && hour <= EVENING_END_HOUR) {
                var kind = "Вечер - отличное время досмотреть видео, которое вы начали! 🎬";
                var aggressive = "Снова бросили видео на середине? Вечер свободен, пора доделать начатое?";

                return Optional.of(createNotification(
                        action.getUserId(),
                        adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                        context.getUser().getToneProfile(),
                        1200 // через 20 минут
                ).build());
            }
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "UNFINISHED_VIDEO";
    }
}