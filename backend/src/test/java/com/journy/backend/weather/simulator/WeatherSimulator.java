package com.journy.backend.weather.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.BackendApplication;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.*;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import com.journy.backend.weather.WeatherAdjustmentService;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Explicit test-classpath launcher. Never present in the production artifact. */
public class WeatherSimulator {
    static final LocalDate DATE = LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(1);
    static final String TRIP_ID = "weather-simulator-trip";
    static final String EMAIL = "weather.simulator@example.test";
    static final String PASSWORD = "WeatherDemo123!";

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        if (args.length > 1 || (port != 8080 && port != 8082)) {
            throw new IllegalArgumentException("Only fixture ports 8080 (simulator) and 8082 (API smoke) are supported");
        }
        // Fail before Spring starts if the normal backend is still using the requested port.
        try (var probe = new java.net.ServerSocket()) {
            probe.bind(new InetSocketAddress("127.0.0.1", port));
        }
        HttpServer stub = forecastStub();
        try {
            System.setProperty("spring.devtools.restart.enabled", "false");
            var context = new SpringApplication(BackendApplication.class, Scenario.class).run(
                    "--spring.profiles.active=weather-simulator",
                    "--server.address=127.0.0.1", "--server.port=" + port,
                    "--spring.datasource.url=jdbc:h2:mem:weather_simulator;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.datasource.username=sa", "--spring.datasource.password=",
                    "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.sql.init.mode=never",
                    "--spring.h2.console.enabled=false",
                    "--journy.weather.open-meteo.enabled=true",
                    "--journy.weather.open-meteo.endpoint=http://127.0.0.1:" + stub.getAddress().getPort() + "/forecast",
                    "--journy.places.osm.enabled=false", "--journy.destinations.nominatim.enabled=false",
                    "--app.ai-agent.base-url=http://127.0.0.1:1");
            context.addApplicationListener(event -> {
                if (event instanceof org.springframework.context.event.ContextClosedEvent) stub.stop(0);
            });
        } catch (Throwable error) {
            stub.stop(0);
            throw error;
        }
    }

    private static HttpServer forecastStub() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", exchange -> {
            Map<String, String> query = new HashMap<>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                query.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            // Only the exact fixture's coordinates and dates have synthetic observations.
            boolean matches = "41.6771".equals(query.get("latitude")) && "26.5557".equals(query.get("longitude"))
                    && DATE.toString().equals(query.get("start_date")) && DATE.toString().equals(query.get("end_date"))
                    && "auto".equals(query.get("timezone"));
            if (!matches) {
                exchange.sendResponseHeaders(422, -1);
                exchange.close();
                return;
            }
            var mapper = new ObjectMapper();
            var root = mapper.createObjectNode();
            root.put("timezone", "Europe/Istanbul");
            root.put("fixture", "DEV ONLY: deterministic positive rain; not a live forecast");
            var hourly = root.putObject("hourly");
            var times = hourly.putArray("time");
            var probabilities = hourly.putArray("precipitation_probability");
            var amounts = hourly.putArray("precipitation");
            var codes = hourly.putArray("weather_code");
            for (int hour = 0; hour < 24; hour++) {
                times.add(DATE.atTime(hour, 0).toString());
                probabilities.add(hour == 14 ? 90 : 0);
                amounts.add(hour == 14 ? 2.0 : 0.0);
                codes.add(hour == 14 ? 63 : 0);
            }
            byte[] body = mapper.writeValueAsBytes(root);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("weather-simulator")
    public static class Scenario {
        @Bean @Order(0)
        SecurityFilterChain fixtureMarkerSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__dev/weather-simulator")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        @Bean
        CommandLineRunner prepare(UserAccountRepository users, PasswordEncoder passwords, PlaceRepository places,
                                  TripRepository trips, ItineraryDayRepository days, TransactionTemplate transactions,
                                  WeatherAdjustmentService weather) {
            return args -> transactions.executeWithoutResult(status -> {
                try {
                    var user = users.save(new UserAccount("DEV ONLY · Rain simulator", EMAIL,
                            passwords.encode(PASSWORD), "Deterministic weather UI test"));
                    var trip = new Trip(user, "Edirne", "", DATE, DATE.plusDays(1), TravelerType.SOLO,
                            BudgetMode.BALANCED, TripPace.BALANCED, Set.of(TravelInterest.WALKING, TravelInterest.MUSEUMS));
                    trip.setId(TRIP_ID);
                    trip.setCurrentTrip(true);
                    trip.setTotalStops(2);
                    var record = new ObjectMapper().readTree(WeatherSimulator.class.getResourceAsStream("/weather-simulator/osm-places.json"));
                    var outdoor = places.save(place(record.path("elements").get(0), PlaceCategory.WALKING, record));
                    var museum = places.save(place(record.path("elements").get(1), PlaceCategory.CULTURE, record));
                    // A two-stop route has the same distance in reverse. Derive its baseline from coordinates.
                    double walking = Math.round(distance(outdoor, museum) * 1.18 * 10) / 10.0;
                    trip.setAverageWalkKm(walking);
                    trips.save(trip);
                    var day = new ItineraryDay(trip, 1, "DEV ONLY · Positive rain", "Recorded OSM places; synthetic weather in isolated memory database.", walking);
                    day.addStop(stop(outdoor, 1, "14:00"));
                    day.addStop(stop(museum, 2, "16:00"));
                    days.saveAndFlush(day);
                    var preview = weather.preview(trip, List.of(day));
                    if (!preview.available()) throw new IllegalStateException("Fixture is not eligible: " + preview.reasons());
                    System.out.println("\nDEV ONLY WEATHER SIMULATOR READY · " + DATE + " · Europe/Istanbul");
                    System.out.println("Rain 14:00–15:00: 90%, 2 mm/h. Dry at 16:00. Default thresholds unchanged.");
                    System.out.println("Login: " + EMAIL + " / " + PASSWORD + " · In-memory fixture only.\n");
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static Place place(JsonNode node, PlaceCategory category, JsonNode record) {
        String identity = node.path("type").asText() + "_" + node.path("id").asLong();
        var place = new Place(node.path("tags").path("name").asText(), "Edirne", category,
                "Recorded OpenStreetMap place; see fixture provenance.", "Mid", 0, "");
        place.setId("osm_" + identity);
        place.setProvider("osm");
        place.setProviderPlaceId(identity.replace('_', '/'));
        place.setLatitude(node.path("lat").asDouble());
        place.setLongitude(node.path("lon").asDouble());
        // The provider snapshot records its retrieval date, not a fabricated exact retrieval time.
        place.setProviderFetchedAt(LocalDate.parse(record.path("retrievedOn").asText()).atStartOfDay(ZoneOffset.UTC).toInstant());
        place.setAddress(null);
        place.setOpeningHours(null); // No invented opening hours or indoor claims from names.
        place.setEstimatedVisitMinutes(category == PlaceCategory.CULTURE ? 120 : 60);
        place.setTags(node.path("tags").has("tourism") ? "tourism:" + node.path("tags").path("tourism").asText()
                : "historic:" + node.path("tags").path("historic").asText());
        return place;
    }

    private static ItineraryStop stop(Place place, int order, String time) {
        return new ItineraryStop(order, place.getName(), place.getCategory().name(), time, place.getId(),
                "provider:osm", "DEV ONLY scenario · recorded OSM identity", place.getLatitude(), place.getLongitude());
    }

    private static double distance(Place first, Place second) {
        double lat = Math.toRadians(second.getLatitude() - first.getLatitude());
        double lon = Math.toRadians(second.getLongitude() - first.getLongitude());
        double a = Math.sin(lat / 2) * Math.sin(lat / 2) + Math.cos(Math.toRadians(first.getLatitude()))
                * Math.cos(Math.toRadians(second.getLatitude())) * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @RestController
    @Profile("weather-simulator")
    public static class Marker {
        @GetMapping("/__dev/weather-simulator")
        public Map<String, Object> marker() {
            return Map.of("fixture", "journy-positive-rain-v1", "production", false,
                    "tripId", TRIP_ID, "date", DATE.toString(), "timezone", "Europe/Istanbul",
                    "rainWindow", "14:00–15:00", "weather", "synthetic development fixture");
        }
    }
}
