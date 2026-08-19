package com.journy.backend.itinerary.dto;

import java.util.List;

public record RightNowResponse(
        boolean available,
        int dayNumber,
        String title,
        String message,
        String recommendationTitle,
        String recommendationMeta,
        String actionLabel,
        String stopId,
        int freeWindowMinutes,
        int delayMinutes,
        List<String> context,
        List<String> reasons
) {
}
