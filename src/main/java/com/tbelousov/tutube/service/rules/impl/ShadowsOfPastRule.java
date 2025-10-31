package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Component
public class ShadowsOfPastRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 91; // 3+ мес
    private static final int VIDEO_DONE_PERCENT = 80;

    private static final Map<String, String> PASSIVE_PAIRS = Map.of(
            "guitar_lessons", "rock_history",
            "coding_tutorials", "tech_history",
            "fitness_workouts", "sports_history"
    );

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // ищем активную тему, по которой был прогресс, затем долгий перерыв, и сейчас - пассивно-смежное
        Optional<String> active = PASSIVE_PAIRS.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(action.getVideoTopic()))
                .map(Map.Entry::getKey).findFirst();

        if (active.isEmpty()) return Optional.empty();

        var activeTopic = active.get();

        // был ли "глубокий прогресс" по activeTopic 3+ мес назад
        var threeMonthsAgo = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);

        boolean hadDepth = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO && activeTopic.equalsIgnoreCase(a.getVideoTopic()))
                .filter(a -> a.getVideoProgress() != null && a.getVideoProgress() >= VIDEO_DONE_PERCENT)
                .anyMatch(a -> a.getTimestamp().isBefore(threeMonthsAgo));

        if (hadDepth) {
            var kind = "Возвращаетесь к теме! Продолжим там, где остановились? Ваш учебный плейлист ждёт.";
            var aggressive = "Бросили занятия, но не можете забыть. Может, вернёмся к делу?";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800
            ).context("resumeTopic=" + activeTopic).build());
        }
        return Optional.empty();
    }

    @Override public String getRuleName() { return "SHADOWS_OF_PAST"; }
}