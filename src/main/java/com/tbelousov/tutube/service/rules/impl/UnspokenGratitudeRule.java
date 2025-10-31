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

@Component
public class UnspokenGratitudeRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 30;
    private static final int VIDEO_DONE_PERCENT = 95;
    private static final double FINISH_SHARE_RATIO_THRESHOLD = .8;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getChannelId() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var since = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<UserAction> recent = context.getRecentActions().stream()
                .filter(a -> a.getTimestamp().isAfter(since) && Objects.equals(a.getChannelId(), action.getChannelId()))
                .toList();

        if (recent.isEmpty()) return Optional.empty();

        long finishes = recent.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> a.getVideoProgress() != null && a.getVideoProgress() >= VIDEO_DONE_PERCENT)
                .count();

        long likes = recent.stream().filter(a -> a.getActionType() == UserAction.ActionType.LIKE_VIDEO).count();
        long comments = recent.stream().filter(a -> a.getActionType() == UserAction.ActionType.COMMENT).count();

        double finishShare = finishes * 1.0 / Math.max(1, recent.stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO).count());

        boolean lowSocial = (likes + comments) <= 1;

        if (finishShare >= FINISH_SHARE_RATIO_THRESHOLD && lowSocial) {
            var kind = "Кажется, вы - тайный поклонник канала. Автору будет приятно простой лайк 😉";
            var aggressive = "Досматриваете почти всё - а лайки где? Не жадничаем, жмём сердечко.";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800
            ).context("channel=" + action.getChannelId()).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "UNSPOKEN_GRATITUDE"; }
}