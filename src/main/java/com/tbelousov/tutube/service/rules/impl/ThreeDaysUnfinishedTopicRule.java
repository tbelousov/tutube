package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;

@Component
public class ThreeDaysUnfinishedTopicRule extends AbstractTriggerRule {

    private static final int MIN_DISTINCT_DAYS = 3;
    private static final int VIDEO_DONE_PERCENT = 80;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var zone = ZoneId.of(context.getUser().getTimezone());
        var today = action.getTimestamp().atZone(zone).toLocalDate();

        long distinctDays = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> action.getVideoTopic().equalsIgnoreCase(a.getVideoTopic()))
                .filter(a -> a.getVideoProgress() != null && a.getVideoProgress() < VIDEO_DONE_PERCENT)
                .filter(a -> !a.getTimestamp().atZone(zone).toLocalDate().isAfter(today))
                .map(a -> a.getTimestamp().atZone(zone).toLocalDate())
                .distinct()
                .count();

        if (distinctDays >= MIN_DISTINCT_DAYS) {
            var kind = "Похоже, длинновато. Предложить короткое видео по теме \"" + action.getVideoTopic() + "\"?";
            var aggressive = "Три дня не досматриваете. Держите коротыш, вдруг осилите.";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800
            ).context("topic=" + action.getVideoTopic()).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "THREE_DAYS_UNFINISHED_TOPIC"; }
}