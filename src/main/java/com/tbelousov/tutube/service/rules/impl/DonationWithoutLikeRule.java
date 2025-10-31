package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DonationWithoutLikeRule extends AbstractTriggerRule {

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.DONATE && canTrigger(context) && action.getChannelId() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Собираем видео канала, которые юзер смотрел
        Set<Long> channelVideos = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> Objects.equals(a.getChannelId(), action.getChannelId()))
                .map(UserAction::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (channelVideos.isEmpty()) {
            return Optional.empty();
        }

        // Собираем лайки, которые юзер ставил
        Set<Long> likedVideos = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.LIKE_VIDEO)
                .map(UserAction::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Если есть просмотренные видео канала, но ни одно не лайкнуто
        boolean hasUnlikedVideos = channelVideos.stream()
                .noneMatch(likedVideos::contains);

        if (hasUnlikedVideos) {
            var kind = "💝 Спасибо за донат! А лайки на видео тоже помогут автору.";
            var aggressive = "Задонатили, а лайки забыли? Интересная логика.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    300
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "DONATION_WITHOUT_LIKE"; }
}