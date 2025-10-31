package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SubscribedButInactiveRule extends AbstractTriggerRule {

    private static final int INACTIVE_DAYS = 21;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // триггерим при любом действии, но проверяем пассивность по каналам с подпиской
        Set<Long> subscribed = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.SUBSCRIBE && a.getChannelId() != null)
                .map(UserAction::getChannelId).collect(Collectors.toSet());

        if (subscribed.isEmpty()) return Optional.empty();

        var since = ago(INACTIVE_DAYS, ChronoUnit.DAYS);

        boolean hasInactiveChannel = subscribed.stream().anyMatch(ch ->
                context.getRecentActions().stream()
                        .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO && Objects.equals(a.getChannelId(), ch))
                        .noneMatch(a -> a.getTimestamp().isAfter(since))
        );

        if (hasInactiveChannel) {
            var kind = "Давно не заглядывали к одному из любимых каналов. Вернуться и посмотреть свеженькое?";
            var aggressive = "Подписка есть, просмотров нет. Это канал вашего друга, верно?";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800
            ).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "SUBSCRIBED_BUT_INACTIVE"; }
}