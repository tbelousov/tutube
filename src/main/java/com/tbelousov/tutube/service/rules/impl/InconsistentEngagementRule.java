package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InconsistentEngagementRule extends AbstractTriggerRule {

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Проверяем при любом действии: был ли вчера лайк комментария без лайка видео
        var yesterday = ago(1, ChronoUnit.DAYS);

        // Находим все лайки комментариев за последние сутки
        List<UserAction> commentLikes = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.LIKE_COMMENT)
                .filter(a -> a.getTimestamp().isAfter(yesterday))
                .filter(a -> a.getVideoId() != null)
                .toList();

        if (commentLikes.isEmpty()) {
            return Optional.empty();
        }

        // Собираем ID всех лайкнутых видео
        Set<Long> likedVideos = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.LIKE_VIDEO)
                .filter(a -> a.getTimestamp().isAfter(yesterday))
                .map(UserAction::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Проверяем, есть ли лайки комментариев, где само видео не лайкнуто
        Optional<UserAction> inconsistentLike = commentLikes.stream()
                .filter(cl -> !likedVideos.contains(cl.getVideoId()))
                .findFirst();

        if (inconsistentLike.isPresent()) {
            var kind = "Вы лайкнули комментарий! А само видео понравилось? 😊";
            var aggressive = "Комментарий залайкали, а видео нет? Странная логика, но ладно.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    600
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "INCONSISTENT_ENGAGEMENT";
    }
}