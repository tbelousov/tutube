package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CommentPopularityRule extends AbstractTriggerRule {
    private static final int A_LOT_OF_LIKES = 10;
    private final UserActionRepository actionRepo;

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return canTrigger(context);
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Подсчитаем, сколько лайков набрал этот комментарий за последний час
        long likesCount = actionRepo.countCommentLikesSince(action.getCommentId(), ago(1, ChronoUnit.HOURS));

        if (likesCount >= A_LOT_OF_LIKES) {
            var kind = "🌟 Ваш комментарий набрал " + likesCount + " лайков! Люди ценят ваше мнение. Напишите ещё?";
            var aggressive = "Ого, " + likesCount + " лайков? Вы теперь знаменитость. Может, ещё что-нибудь гениальное напишете?";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    300 // через 5 минут
            ).build());
        }

        return Optional.empty();
    }

    @Override
    public String getRuleName() {
        return "COMMENT_POPULARITY";
    }
}