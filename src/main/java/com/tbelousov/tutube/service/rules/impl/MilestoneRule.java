package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MilestoneRule extends AbstractTriggerRule {

    private static final List<Long> MAGIC_NUMBERS = List.of(9L, 49L, 99L, 199L, 499L, 999L);

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getVideoTopic() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Подсчитаем просмотры по темам
        Map<String, Long> topicCounts = Stream.concat(
                context.getRecentActions().stream()
                        .filter(a -> a.getActionType() == UserAction.ActionType.VIEW_VIDEO && a.getVideoTopic() != null),
                Stream.of(action)
        ).collect(Collectors.groupingBy(UserAction::getVideoTopic, Collectors.counting()));

        var topic = action.getVideoTopic();
        long count = topicCounts.getOrDefault(topic, 0L);

        if (MAGIC_NUMBERS.contains(count)) {
            var kind = "🎯 Вы посмотрели уже " + count + " видео про " + topic +
                    "! Ещё одно - и вы настоящий эксперт!";
            var aggressive = count + " видео про " + topic + "? Ещё одно и можете в резюме писать 'гуру'. Дерзайте.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    300
            ).context("topic:" + topic + ",count:" + (count + 1)).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "MILESTONE";
    }
}