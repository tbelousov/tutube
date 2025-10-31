package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WeatherBasedRule extends AbstractTriggerRule {

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return canTrigger(context) && action.getWeather() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        var weather = action.getWeather().toLowerCase();
        if (weather.contains("rain") || weather.contains("cold") || weather.contains("snow")) {
            var kind = "☕ На улице " + translateWeather(weather) +
                    "! Идеальная погода для просмотра уютных видео с чашкой горячего напитка.";
            var aggressive = "Погода отвратительная, зато можно сидеть дома и смотреть видосики.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    900
            ).context("weather:" + weather).build());
        }

        return Optional.empty();
    }

    private String translateWeather(String weather) {
        if (weather.contains("rain")) return "дождь";
        if (weather.contains("cold")) return "холодно";
        if (weather.contains("snow")) return "снег";
        return weather;
    }

    @Override
    public String getRuleName() {
        return "WEATHER_BASED";
    }
}