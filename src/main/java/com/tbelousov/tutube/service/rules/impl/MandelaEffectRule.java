package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class MandelaEffectRule extends AbstractTriggerRule {

    private static final int MIN_TOPIC_VIEWS = 3;
    private static final double IMPATIENCE_THRESHOLD = .01; // ~ 1 перемотка на 100с в среднем

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var since = ago(2, ChronoUnit.HOURS);

        List<UserAction> topicWatches = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> action.getVideoTopic().equalsIgnoreCase(a.getVideoTopic()))
                .filter(a -> a.getTimestamp().isAfter(since))
                .toList();

        if (topicWatches.size() < MIN_TOPIC_VIEWS) return Optional.empty();

        // "Индекс нетерпения" ~ seeks/time
        double avgImpatience = topicWatches.stream()
                .filter(a -> a.getWatchDurationSec() != null && a.getWatchDurationSec() > 0)
                .mapToDouble(a -> (a.getSeeksCount() == null ? 0 : a.getSeeksCount()) / (double) a.getWatchDurationSec())
                .average().orElse(0);

        boolean searchingPattern = avgImpatience > IMPATIENCE_THRESHOLD;

        boolean thenCommented = context.getRecentActions().stream()
                .anyMatch(a -> a.getActionType() == UserAction.ActionType.COMMENT && a.getTimestamp().isAfter(since));

        if (searchingPattern && thenCommented) {
            var kind = "Ищете подтверждение старой памяти? Это может быть «эффект Манделы». Вот разбор темы.";
            var aggressive = "Опять гоняетесь за «как было на самом деле»? Гляньте объяснение эффекта Манделы.";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    900
            ).context("topic=" + action.getVideoTopic()).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "MANDELA_EFFECT"; }
}