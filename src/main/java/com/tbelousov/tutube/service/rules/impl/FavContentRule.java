package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class FavContentRule extends AbstractTriggerRule {

    private static final int MIN_TOPICS = 3;
    private static final int MIN_TOP_TOPIC_VIEWS = 3;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var topic = action.getVideoTopic();

        // Находим топ-тему
        Map<String, Long> topicCounts = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO)
                .filter(a -> a.getVideoTopic() != null)
                .collect(Collectors.groupingBy(UserAction::getVideoTopic, Collectors.counting()));

        Optional<String> topTopic = topicCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);

        if (topicCounts.size() >= MIN_TOPICS && topTopic.isPresent() && topTopic.get().equals(topic) && topicCounts.get(topTopic.get()) >= MIN_TOP_TOPIC_VIEWS) {
            var kind = "🎯 Вам нравится тема '" + topic + "'! Если честно, нам тоже.";
            var aggressive = "Снова '" + topic + "'? Может стоит попробовать что-то другое?";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1200
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "FAV_CONTENT";
    }
}