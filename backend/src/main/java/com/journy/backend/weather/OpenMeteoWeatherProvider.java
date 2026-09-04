package com.journy.backend.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;

@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {
    private final RestClient client;
    private final boolean enabled;
    private final String endpoint;
    private final Clock clock;
    public OpenMeteoWeatherProvider(RestClient.Builder builder,
            @Value("${journy.weather.open-meteo.enabled:true}") boolean enabled,
            @Value("${journy.weather.open-meteo.endpoint:https://api.open-meteo.com/v1/forecast}") String endpoint,
            @Qualifier("weatherClock") Clock clock) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = builder.defaultHeader("User-Agent", "Journy/1.0 weather-provider").requestFactory(factory).build();
        this.enabled = enabled; this.endpoint = endpoint; this.clock = clock;
    }
    @Override public WeatherForecast forecast(double lat, double lon, LocalDate start, LocalDate end, String timezone) {
        if (!enabled) return unavailable("PROVIDER_DISABLED", lat, lon, start, end);
        // A broad UTC guard avoids pointless remote calls without guessing the destination timezone.
        LocalDate utc = LocalDate.now(clock);
        if (start == null || end == null || end.isBefore(start) || start.isBefore(utc.minusDays(1)) || end.isAfter(utc.plusDays(16)))
            return unavailable("OUTSIDE_FORECAST_HORIZON", lat, lon, start, end);
        try {
            var uri = UriComponentsBuilder.fromUriString(endpoint).queryParam("latitude", lat).queryParam("longitude", lon)
                    .queryParam("start_date", start).queryParam("end_date", end).queryParam("timezone", timezone)
                    .queryParam("precipitation_unit", "mm")
                    .queryParam("hourly", "precipitation_probability,precipitation,weather_code").build().encode().toUri();
            LoggerFactory.getLogger(getClass()).info("weather_request latitude={} longitude={} start={} end={} timezone={}", lat, lon, start, end, timezone);
            JsonNode root = client.get().uri(uri).retrieve().body(JsonNode.class);
            WeatherForecast result = parse(root, lat, lon, start, end);
            LoggerFactory.getLogger(getClass()).info("weather_response latitude={} longitude={} status={} reason={} timezone={} hours={}",
                    lat, lon, result.status(), result.reason(), result.timezone(), result.hours().size());
            return result;
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(getClass()).warn("weather_unavailable latitude={} longitude={} start={} end={} error={}",lat,lon,start,end,e.toString());
            return unavailable("PROVIDER_FAILURE",lat,lon,start,end);
        }
    }
    WeatherForecast parse(JsonNode root, double lat, double lon, LocalDate start, LocalDate end) {
        try {
            if (root == null || root.path("error").asBoolean(false)) return unavailable("INVALID_FORECAST",lat,lon,start,end);
            ZoneId zone = ZoneId.of(root.path("timezone").asText());
            LocalDate today = LocalDate.now(clock.withZone(zone));
            if (start.isBefore(today) || end.isAfter(today.plusDays(15))) return unavailable("OUTSIDE_FORECAST_HORIZON",lat,lon,start,end);
            var hourly = root.path("hourly"); var times = hourly.path("time");
            var probabilities = hourly.path("precipitation_probability"); var amounts = hourly.path("precipitation"); var codes = hourly.path("weather_code");
            if (!times.isArray() || times.isEmpty() || !probabilities.isArray() || !amounts.isArray()
                    || probabilities.size()!=times.size() || amounts.size()!=times.size()) return unavailable("INCOMPLETE_FORECAST",lat,lon,start,end);
            List<WeatherForecast.Hour> hours = new ArrayList<>(); Set<LocalDateTime> seen = new HashSet<>();
            for (int i=0;i<times.size();i++) {
                LocalDateTime time = LocalDateTime.parse(times.get(i).asText());
                if (time.toLocalDate().isBefore(start) || time.toLocalDate().isAfter(end)) continue;
                if (!probabilities.get(i).isNumber() || !amounts.get(i).isNumber()) return unavailable("INCOMPLETE_FORECAST",lat,lon,start,end);
                double probability=probabilities.get(i).asDouble(), amount=amounts.get(i).asDouble();
                if (!Double.isFinite(probability) || probability<0 || probability>100 || !Double.isFinite(amount) || amount<0
                        || time.getMinute()!=0 || !seen.add(time)) return unavailable("INVALID_FORECAST",lat,lon,start,end);
                Integer code = codes.isArray() && i<codes.size() && codes.get(i).isIntegralNumber() ? codes.get(i).asInt() : null;
                hours.add(new WeatherForecast.Hour(time,probability,amount,code));
            }
            // Only complete local-hour coverage is treated as reliable, including dry hours used by a preview.
            for (LocalDate date=start; !date.isAfter(end); date=date.plusDays(1))
                for (int hour=0;hour<24;hour++) if (!seen.contains(date.atTime(hour,0))) return unavailable("INCOMPLETE_FORECAST",lat,lon,start,end);
            hours.sort(Comparator.comparing(WeatherForecast.Hour::time));
            return new WeatherForecast("AVAILABLE","FORECAST_AVAILABLE","Open-Meteo",lat,lon,zone.getId(),start,end,List.copyOf(hours));
        } catch (RuntimeException e) { return unavailable("INVALID_FORECAST",lat,lon,start,end); }
    }
    private WeatherForecast unavailable(String reason,double lat,double lon,LocalDate start,LocalDate end) {
        return WeatherForecast.unavailable(reason,lat,lon,start,end);
    }
}
