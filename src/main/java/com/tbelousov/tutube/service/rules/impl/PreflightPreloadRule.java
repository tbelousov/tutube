package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

@Component
public class PreflightPreloadRule extends AbstractTriggerRule {

    private static final int MIN_HOURS_FOR_CHANGE_LOCATION = 2;
    private static final int MIN_LOCATIONS_TODAY = 2; // 2 локации = 1 смена
    private static final int MIN_LOCATIONS_TOTAL = 5;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // частые перемещения за 30 дней
        long distinctLocations = context.getRecentActions().stream()
                .map(UserAction::getLocation).filter(Objects::nonNull).distinct().count();

        // резкая смена места за последние 2 часа и мобильное устройство - "в пути"
        boolean recentRelocation = context.getRecentActions().stream()
                .filter(a -> a.getTimestamp().isAfter(ago(MIN_HOURS_FOR_CHANGE_LOCATION, ChronoUnit.HOURS)))
                .map(UserAction::getLocation).filter(Objects::nonNull).distinct().count() >= MIN_LOCATIONS_TODAY;

        boolean onMobile = action.getDeviceType() == UserAction.DeviceType.MOBILE;

        if (distinctLocations >= MIN_LOCATIONS_TOTAL && recentRelocation && onMobile) {
            var kind = "Скоро в дорогу? Предзагрузите видео, чтобы смотреть офлайн без интернета ✈️";
            var aggressive = "Опять куда-то летите. Закачайте видео заранее - потом не нойте про Wi-Fi.";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    15 * 60  // через 15 минут
            ).context("preload=true").build());
        }
        return Optional.empty();
    }

    @Override public String getRuleName() { return "PREFLIGHT_PRELOAD"; }
}