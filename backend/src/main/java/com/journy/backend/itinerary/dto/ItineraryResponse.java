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
            List<ItineraryStopResponse> stops
    ) {
    }

    public record ItineraryStopResponse(
            int order,
            String title,
            String category,
            String timeWindow,
            String note,
            double latitude,
            double longitude
    ) {
    }
}
