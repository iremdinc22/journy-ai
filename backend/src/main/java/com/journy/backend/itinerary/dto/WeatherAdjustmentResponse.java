package com.journy.backend.itinerary.dto;

import java.util.List;

public record WeatherAdjustmentResponse(
        boolean available,
        int dayNumber,
        String rainWindow,
        String title,
        String message,
        String affectedStop,
        String indoorAlternative,
        int beforeStopCount,
        double beforeWalkKm,
        int afterStopCount,
        double afterWalkKm,
        List<String> changes,
        List<String> reasons
) {
}
