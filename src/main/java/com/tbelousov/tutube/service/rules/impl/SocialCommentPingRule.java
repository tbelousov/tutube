package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SocialCommentPingRule extends AbstractTriggerRule {

    private static final int A_LOT_OF_LIKES = 10;
    private static final int LOOKBACK_HOURS = 12;

    private final UserActionRepository actionRepo;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context)
                && action.getVideoId() != null && action.getChannelId() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        boolean currentIsSubscriber = context.getRecentActions().stream()
                .anyMatch(a -> a.getActionType() == UserAction.ActionType.SUBSCRIBE && Objects.equals(a.getChannelId(), action.getChannelId()));

        if (!currentIsSubscriber) return Optional.empty();

        var since = ago(LOOKBACK_HOURS, ChronoUnit.HOURS);
        List<UserAction> otherComments = actionRepo.findOtherUsersCommentsOnVideo(action.getVideoId(), action.getUserId(), since);

        Optional<UserAction> hotComment = otherComments.stream()
                .filter(oc -> actionRepo.isUserSubscribedToChannel(oc.getUserId(), action.getChannelId()))
                .map(oc -> Map.entry(oc, likesForComment(context, oc.getCommentId(), since)))
                .filter(e -> e.getValue() >= A_LOT_OF_LIKES)
                .map(Map.Entry::getKey)
                .findFirst();

        if (hotComment.isPresent()) {
            var kind = "Популярный комментарий от другого подписчика канала. Заглянете в дискуссию?";
            var aggressive = "Другой подписчик набрал кучу лайков в комментах. Не хотите вступить в бой?";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    900
            ).context("videoId=" + action.getVideoId()).build());
        }
        return Optional.empty();
    }

    private long likesForComment(RuleContext context, Long commentId, Instant since) {
        if (commentId == null) return 0;
        return context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.LIKE_COMMENT)
                .filter(a -> a.getCommentId() != null && a.getCommentId().equals(commentId))
                .filter(a -> a.getTimestamp().isAfter(since))
                .count();
    }

    @Override
    public String getRuleName() { return "SOCIAL_COMMENT_PING"; }
}