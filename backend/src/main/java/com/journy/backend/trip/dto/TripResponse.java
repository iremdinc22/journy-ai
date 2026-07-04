package com.journy.backend.trip.dto;

import java.time.LocalDate;
import java.util.List;

public record TripResponse(
        String id,
        String destination,
        String startingArea,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String travelerType,
        String budget,
        String pace,
        List<String> interests,
        TripStats stats
) {
    public record TripStats(
            int stops,
            int foodPicks,
            double averageWalkKm
    ) {
    }
}
