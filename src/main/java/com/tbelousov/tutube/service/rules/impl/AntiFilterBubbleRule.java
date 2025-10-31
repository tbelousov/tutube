package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class AntiFilterBubbleRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 30;
    private static final int FLIP_LIMIT = 3;
    private static final Map<String, String> OPPOSITES = Map.of(
            "vegan", "keto",
            "android", "ios",
            "left_politics", "right_politics"
    );

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // смотрит подряд противоположные темы хотя бы 3 раза за 30 дней
        var since = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<String> seq = Stream.concat(
                context.getRecentActions().stream()
                        .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO && a.getTimestamp().isAfter(since))
                        .map(UserAction::getVideoTopic),
                Stream.of(action.getVideoTopic())
        ).filter(Objects::nonNull).toList();

        int flips = 0;
        for (int i = 1; i < seq.size(); i++) {
            String prev = seq.get(i - 1), cur = seq.get(i);
            if (isOpposite(prev, cur)) flips++;
        }

        if (flips >= FLIP_LIMIT) {
            var kind = "Восхищаемся вашей широтой взглядов! Важно знать все точки зрения.";
            var aggressive = "Нарочно спорите с алгоритмом - круто. Спрячем «альтернативы» от надоевшего канала?";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1200
            ).build());
        }
        return Optional.empty();
    }

    private boolean isOpposite(String a, String b) {
        return OPPOSITES.getOrDefault(a, "").equalsIgnoreCase(b) || OPPOSITES.getOrDefault(b, "").equalsIgnoreCase(a);
    }

    @Override
    public String getRuleName() { return "ANTI_FILTER_BUBBLE"; }
}