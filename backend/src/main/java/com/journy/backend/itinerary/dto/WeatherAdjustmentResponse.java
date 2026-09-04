package com.journy.backend.itinerary.dto;

import com.journy.backend.weather.WeatherForecast;
import java.util.List;

public record WeatherAdjustmentResponse(
        boolean available, int dayNumber, String rainWindow, String title, String message,
        String affectedStop, String indoorAlternative, int beforeStopCount, double beforeWalkKm,
        int afterStopCount, double afterWalkKm, List<String> changes, List<String> reasons,
        String weatherStatus, String previewId, List<StopChange> stopChanges, WeatherForecast forecast
) {
    public record StopChange(String stopId, String placeId, String name, String fromTime, String toTime) {}
}
