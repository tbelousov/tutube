package com.tbelousov.tutube.dto.validator;

import com.tbelousov.tutube.dto.CreateActionRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ActionRequestValidator implements ConstraintValidator<ValidActionRequest, CreateActionRequest> {
    @Override
    public boolean isValid(CreateActionRequest r, ConstraintValidatorContext ctx) {
        if (r.actionType() == null) return false;
        var ok = switch (r.actionType()) {
            case COMMENT -> r.channelId() != null && r.videoId() != null;
            case VIEW_VIDEO -> r.videoId() != null;
            case LIKE_COMMENT -> r.commentId() != null;
            case DONATE -> r.donationAmount() != null && r.donationAmount() >= 0;
            default -> true;
        };
        if (!ok) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate("Required fields are missing for actionType=" + r.actionType())
                    .addConstraintViolation();
        }
        return ok;
    }
}