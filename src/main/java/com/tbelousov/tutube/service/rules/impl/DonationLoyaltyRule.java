package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DonationLoyaltyRule extends AbstractTriggerRule {

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getChannelId() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Найдём каналы, которым пользователь донатил
        Set<Long> donatedChannels = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.DONATE)
                .map(UserAction::getChannelId)
                .collect(Collectors.toSet());

        if (donatedChannels.contains(action.getChannelId())) {
            var kind = "💝 Ещё раз спасибо за поддержку канала! Наслаждайтесь нашими видео!";
            var aggressive = "О, канал, которому вы так великодушно задонатили. Заслужит ли новое видео донат тоже?";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    900
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "DONATION_LOYALTY";
    }
}