package com.journy.backend.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationCoordinates;
import com.journy.backend.trip.model.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class WeatherForecastService {
    private final RestClient restClient;
    private final DestinationCoordinateResolver coordinateResolver;
    private final boolean enabled;
    private final String endpoint;

    public WeatherForecastService(
            RestClient.Builder restClientBuilder,
            DestinationCoordinateResolver coordinateResolver,
            @Value("${journy.weather.open-meteo.enabled:true}") boolean enabled,
            @Value("${journy.weather.open-meteo.endpoint:https://api.open-meteo.com/v1/forecast}") String endpoint
    ) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Journy/1.0 weather-provider")
                .requestFactory(requestFactory())
                .build();
        this.coordinateResolver = coordinateResolver;
        this.enabled = enabled;
        this.endpoint = endpoint;
    }

    public Optional<RainForecast> rainForecastFor(Trip trip, int dayNumber) {
        if (!enabled || trip == null || trip.getDestination() == null || trip.getDestination().isBlank()) {
            return Optional.empty();
        }
        try {
            DestinationCoordinates coordinates = coordinateResolver.coordinatesFor(trip.getDestination());
            JsonNode response = restClient.get()
                    .uri(uriFor(coordinates))
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response, trip.getStartDate().plusDays(Math.max(0, dayNumber - 1)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return factory;
    }

    private String uriFor(DestinationCoordinates coordinates) {
        return endpoint
                + "?latitude=" + coordinates.latitude()
                + "&longitude=" + coordinates.longitude()
                + "&hourly=precipitation_probability,rain,weather_code"
                + "&forecast_days=7"
                + "&timezone=auto";
    }

    private Optional<RainForecast> parse(JsonNode response, LocalDate targetDate) {
        JsonNode hourly = response == null ? null : response.path("hourly");
        if (hourly == null || hourly.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode times = hourly.path("time");
        JsonNode probabilities = hourly.path("precipitation_probability");
        JsonNode rain = hourly.path("rain");
        JsonNode weatherCodes = hourly.path("weather_code");
        if (!times.isArray() || times.isEmpty()) {
            return Optional.empty();
        }

        RainHour best = null;
        for (int index = 0; index < times.size(); index++) {
            String time = times.get(index).asText("");
            if (!time.startsWith(targetDate.toString())) {
                continue;
            }
            int probability = valueAt(probabilities, index, 0);
            double rainMm = doubleAt(rain, index, 0);
            int weatherCode = valueAt(weatherCodes, index, 0);
            int score = probability + (int) Math.round(rainMm * 35) + (isRainCode(weatherCode) ? 20 : 0);
            if (best == null || score > best.score()) {
                best = new RainHour(time, probability, rainMm, weatherCode, score);
            }
        }

        if (best == null || best.score() < 35) {
            return Optional.empty();
        }
        String hour = best.time().substring(11, 13);
        int startHour = Math.max(9, Math.min(18, Integer.parseInt(hour)));
        int endHour = Math.min(22, startHour + 3);
        return Optional.of(new RainForecast(
                "%02d:00 - %02d:00".formatted(startHour, endHour),
                best.probability(),
                best.rainMm(),
                best.weatherCode(),
                "Open-Meteo"
        ));
    }

    private int valueAt(JsonNode node, int index, int fallback) {
        return node != null && node.isArray() && index < node.size() && !node.get(index).isNull()
                ? node.get(index).asInt(fallback)
                : fallback;
    }

    private double doubleAt(JsonNode node, int index, double fallback) {
        return node != null && node.isArray() && index < node.size() && !node.get(index).isNull()
                ? node.get(index).asDouble(fallback)
                : fallback;
    }

    private boolean isRainCode(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99);
    }

    private record RainHour(String time, int probability, double rainMm, int weatherCode, int score) {
    }

    public record RainForecast(
            String rainWindow,
            int precipitationProbability,
            double rainMm,
            int weatherCode,
            String source
    ) {
    }
}
