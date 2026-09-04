package com.journy.backend.weather;
import java.time.LocalDate;
public interface WeatherProvider {
    WeatherForecast forecast(double latitude, double longitude, LocalDate start, LocalDate end, String timezone);
}
