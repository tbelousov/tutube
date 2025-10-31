package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class RepeatedVideoWatchRule extends AbstractTriggerRule {

    private static final int VIEWS_THRESHOLD = 3;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var zone = ZoneId.of(context.getUser().getTimezone());
        var target = action.getTimestamp().atZone(zone).toLocalDate();

        // Проверяем пересмотры одного и того же видео за день
        long count = Stream.concat(
                context.getRecentActions().stream()
                        .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                        .filter(a -> Objects.equals(a.getVideoId(), action.getVideoId()))
                        .filter(a -> a.getTimestamp().atZone(zone).toLocalDate().equals(target)),
                Stream.of(action)
        ).count();

        if (count >= VIEWS_THRESHOLD) {
            var message = "Я просто поиграю на фоне, не обращайте внимания 😊";
            var aggressiveMessage = "Серьёзно? Снова это видео? Вы что, зациклились?";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(message, aggressiveMessage, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    3600 // через 1 час
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "REPEATED_VIDEO_WATCH";
    }
}