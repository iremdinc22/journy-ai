package com.journy.backend.trip.dto;

public record TripPreviewResponse(
        int estimatedStops,
        double dailyWalkKm,
        String routeStyle,
        int availablePlaceCount,
        String confidence,
        String summary
) {
}
