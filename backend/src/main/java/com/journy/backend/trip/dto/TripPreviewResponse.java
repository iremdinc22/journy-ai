package com.journy.backend.trip.dto;

public record TripPreviewResponse(
        int estimatedStops,
        double dailyWalkKm,
        String dailyWalkRange,
        String routeStyle,
        int availablePlaceCount,
        int matchedPlaceCount,
        String confidence,
        String summary,
        String planningStyle,
        String startingAreaInsight
) {
}
