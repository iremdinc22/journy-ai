package com.journy.backend.itinerary.dto;

import java.util.List;

public record ItineraryResponse(
        String tripId,
        String destination,
        List<ItineraryDayResponse> days
) {
    public record ItineraryDayResponse(
            int dayNumber,
            String title,
            String summary,
            double walkKm,
            int stopCount,
            List<ItineraryStopResponse> stops,
            List<ItineraryTimelineItemResponse> timeline,
            java.util.Map<String, String> titleTranslations
    ) {
    }

    public record ItineraryStopResponse(
            String id,
            int order,
            String title,
            String category,
            String timeWindow,
            String placeId,
            String source,
            String note,
            boolean optional,
            String status,
            String arrivedAt,
            String completedAt,
            double latitude,
            double longitude
    ) {
    }

    public record ItineraryTimelineItemResponse(
            String id,
            String type,
            String title,
            String startTime,
            String endTime,
            int durationMinutes,
            Double distanceKm,
            String fromStopId,
            String toStopId,
            String category,
            String note,
            String constraintStatus,
            String constraintWarning
    ) {
    }
}
