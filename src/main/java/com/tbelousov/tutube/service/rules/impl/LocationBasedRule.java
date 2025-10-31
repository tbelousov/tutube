package com.tbelousov.tutube.service.rules.impl;

import com.tbelousov.tutube.entity.Notification;
import com.tbelousov.tutube.entity.UserAction;
import com.tbelousov.tutube.service.rules.AbstractTriggerRule;
import com.tbelousov.tutube.service.rules.RuleContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
public class LocationBasedRule extends AbstractTriggerRule {

    private static final double DISTANCE_THRESHOLD_METERS = 100_000.0; // 100 км

    @Override
    protected boolean isApplicable(UserAction action, RuleContext context) {
        return action.getActionType() == UserAction.ActionType.VIEW_VIDEO && canTrigger(context) && action.getLocation() != null;
    }

    @Override
    protected Optional<Notification> evaluatePattern(UserAction action, RuleContext context) {
        // Если локация изменилась
        Optional<String> previousLocation = context.getRecentActions().stream()
                .filter(a -> a.getLocation() != null)
                .filter(a -> a.getTimestamp().isBefore(action.getTimestamp()))
                .max(Comparator.comparing(UserAction::getTimestamp))
                .map(UserAction::getLocation);

        // Сначала просто сравниваем
        boolean locationChanged = previousLocation.isPresent()
                && !previousLocation.get().equals(action.getLocation());

        // Если обе локации - координаты, то пробуем вычислить расстояние
        // А если нет - то и бог с ним, это тестовый проект
        boolean anotherCity = true;
        if (locationChanged) {
            try {
                double[] prev = parseLocation(previousLocation.get());
                double[] curr = parseLocation(action.getLocation());

                double distance = haversine(prev[0], prev[1], curr[0], curr[1]);
                anotherCity = distance > DISTANCE_THRESHOLD_METERS;
            } catch (Exception e) {
                // ignored
            }
        }

        if (locationChanged && anotherCity) {
            var kind = "🗺️ Новое место? Может, интересно узнать о нём больше?";
            var aggressive = "И куда это вы поехали? Держите гид по местным достопримечательностям.";

            return Optional.of(createNotification(
                    action.getUserId(),
                    adaptTone(kind, aggressive, context.getUser().getToneProfile()),
                    context.getUser().getToneProfile(),
                    1800
            ).build());
        }

        return Optional.empty();
    }

    private static double[] parseLocation(String location) {
        // Ожидаемый формат: "lat,lng"
        var parts = location.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid location format: " + location);
        }
        return new double[] {
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim())
        };
    }

    // Формула гаверсинуса - вычисляет расстояние между двумя точками на сфере (в метрах)
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // радиус Земли, м
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public String getRuleName() {
        return "LOCATION_BASED";
    }
}