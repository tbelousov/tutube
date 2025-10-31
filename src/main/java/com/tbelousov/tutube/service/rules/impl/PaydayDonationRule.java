package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaydayDonationRule extends AbstractTriggerRule {

    private static final int MIN_DONATION_MONTHS = 3;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Анализируем паттерн донатов по дням месяца
        Map<Integer, Long> donationsByDay = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.DONATE)
                .collect(Collectors.groupingBy(
                        a -> a.getTimestamp()
                                .atZone(ZoneId.of(context.getUser().getTimezone()))
                                .getDayOfMonth(),
                        Collectors.counting()
                ));

        // Находим день с максимальными донатами
        Optional<Map.Entry<Integer, Long>> mostFrequentDay = donationsByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        if (mostFrequentDay.isPresent() && mostFrequentDay.get().getValue() >= MIN_DONATION_MONTHS) {
            int payDay = mostFrequentDay.get().getKey();
            int today = action.getTimestamp()
                    .atZone(ZoneId.of(context.getUser().getTimezone()))
                    .getDayOfMonth();

            // Если сегодня "день зарплаты", но не задонатил
            if (today == payDay) {
                boolean donatedToday = context.getRecentActions().stream()
                        .anyMatch(a -> a.getActionType() == UserAction.ActionType.DONATE
                                && a.getTimestamp().isAfter(
                                action.getTimestamp().minus(24, ChronoUnit.HOURS)));

                if (!donatedToday && action.getChannelId() != null) {
                    var kind = "💰 Обычно вы поддерживаете каналы в эти дни. Может, поможем любимым авторам?";
                    var aggressive = "Зарплата пришла, а донатов не видно. Любимые каналы скучают.";

                    return Optional.of(createNotification(
                            action.getUserId(),
                            adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                            context.getUser().getToneProfile(),
                            3600
                    ).build());
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "PAYDAY_DONATION";
    }
}