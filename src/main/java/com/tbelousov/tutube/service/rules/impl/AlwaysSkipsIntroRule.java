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
public class AlwaysSkipsIntroRule extends AbstractTriggerRule {

    private static final int LOOKBACK_DAYS = 30;
    private static final int INTRO_SKIP_THRESHOLD_SEC = 10;   // считаем, что интро есть, если скипнули >10с
    private static final int MIN_CHANNEL_VIEWS_REQUIRED = 3;
    private static final double SKIP_RATIO_THRESHOLD = .8;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getChannelId() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var since = ago(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<UserAction> channelViews = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> a.getChannelId() != null && a.getChannelId().equals(action.getChannelId()))
                .filter(a -> a.getTimestamp().isAfter(since))
                .toList();

        long samples = channelViews.size();
        long skipped = channelViews.stream()
                .filter(a -> a.getIntroSkipSec() != null && a.getIntroSkipSec() > INTRO_SKIP_THRESHOLD_SEC)
                .count();

        if (samples >= MIN_CHANNEL_VIEWS_REQUIRED && skipped * 1.0 / samples >= SKIP_RATIO_THRESHOLD) {
            var kind = "Вы часто пропускаете интро на этом канале. Подскажем автору сократить вступление?";
            var aggressive = "Интро опять промотали? Может, напишем автору, что его заставка - вечность.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1200
            ).context("channelId=" + action.getChannelId()).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "ALWAYS_SKIPS_INTRO"; }
}