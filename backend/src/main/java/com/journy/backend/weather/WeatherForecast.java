package com.journy.backend.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Provider-independent values; missing data is never converted to precipitation zero. */
public record WeatherForecast(String status, String reason, String source, Double latitude, Double longitude,
                              String timezone, LocalDate startDate, LocalDate endDate, List<Hour> hours) {
    public record Hour(LocalDateTime time, Double precipitationProbability, Double precipitationAmount, Integer weatherCode) {}
    public static WeatherForecast unavailable(String reason, Double lat, Double lon, LocalDate start, LocalDate end) {
        return new WeatherForecast("UNAVAILABLE", reason, "Open-Meteo", lat, lon, null, start, end, List.of());
    }
}
