package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class NegativeSpiralRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 7;
    private static final int SENTIMENT_THRESHOLD = -30;
    private static final int MIN_ANGRY_COMMENTS = 5;
    private static final double NEGATIVE_RATIO_THRESHOLD = .7;
    private static final int MIN_CONTROVERSIAL_TOPICS = 2;
    private static final Set<String> CONTROVERSIAL = Set.of("politics", "ideology", "conflict", "debate");

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var since = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<UserAction> lastWeek = context.getRecentActions().stream()
                .filter(a -> a.getTimestamp().isAfter(since)).toList();

        long comments = lastWeek.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT && a.getSentimentScore() != null)
                .count();
        long negative = lastWeek.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT && a.getSentimentScore() != null && a.getSentimentScore() < SENTIMENT_THRESHOLD)
                .count();

        boolean mostlyNegative = comments >= MIN_ANGRY_COMMENTS && negative * 1.0 / comments > NEGATIVE_RATIO_THRESHOLD;

        long controversialTopics = lastWeek.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .map(UserAction::getVideoTopic).filter(Objects::nonNull)
                .filter(t -> CONTROVERSIAL.contains(t.toLowerCase()))
                .distinct().count();

        if (mostlyNegative && controversialTopics >= MIN_CONTROVERSIAL_TOPICS) {
            var kind = "Некоторые темы вызывают бурю эмоций. Сделаем паузу и включим что-то умиротворяющее?";
            var aggressive = "Кажется, вы зашли в спираль негатива. Переключаемся на природу/ASMR - иначе крышу сорвёт.";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    600
            ).context("playlist=calm").build());
        }
        return Optional.empty();
    }

    @Override public String getRuleName() { return "NEGATIVE_SPIRAL"; }
}