package com.tbelousov.tutube.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UserContext {
    private Long userId;
    private Map<String, Object> behaviorPatterns;
    private String currentLocation;
    private String currentWeather;
    private String preferredTone;

    public String toPromptString() {
        var sb = new StringBuilder();
        sb.append("ID юзера: ").append(userId).append("\n");
        sb.append("Шаблоны поведения:\n");
        behaviorPatterns.forEach((k, v) ->
                sb.append("- ").append(k).append(": ").append(v).append("\n"));
        if (currentLocation != null) {
            sb.append("Локация: ").append(currentLocation).append("\n");
        }
        if (currentWeather != null) {
            sb.append("Погода: ").append(currentWeather).append("\n");
        }
        sb.append("Tone preference: ").append(preferredTone);
        return sb.toString();
    }
}