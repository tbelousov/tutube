package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class GrammarHelpRule extends AbstractTriggerRule {

    private static final int TYPOS_THRESHOLD = 10; // эвристика на одно сообщение
    private static final int MIN_COMMENTS_WITH_TYPOS = 2;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.COMMENT && canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var since = ago(30, ChronoUnit.DAYS);

        long commentsWithTyposCounts = context.getRecentActions().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT)
                .filter(a -> a.getTimestamp().isAfter(since))
                .filter(a -> a.getTyposCount() != null && a.getTyposCount() >= TYPOS_THRESHOLD)
                .count();

        if (action.getTyposCount() != null && action.getTyposCount() >= TYPOS_THRESHOLD && commentsWithTyposCounts >= MIN_COMMENTS_WITH_TYPOS) {
            var kind = "Замечаем опечатки в комментариях. Подкинуть короткое видео о грамматике?";
            var aggressive = "Столько опечаток - глаза плачут. Хотите видео, как не позориться в комментах?";
            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    900
            ).context("typos=" + action.getTyposCount()).build());
        }
        return Optional.empty();
    }

    @Override
    public String getRuleName() { return "GRAMMAR_HELP"; }
}