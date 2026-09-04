package com.journy.backend.controller;

import com.fasterxml.jackson.databind.*;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real resolver, HTTP adapters, cache, planner and API; only the network uses local fixtures. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phase11_matrix;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UniversalDestinationPipelineIntegrationTest {
    record City(String raw, String locality, double lat, double lon, int id) {}
    static final List<City> CITIES = List.of(
        new City("Las Vegas", "Las Vegas", 36.1674263, -115.1484131, 1),
        new City("Tallinn", "Tallinn", 59.437242, 24.7572693, 2),
        new City("Bologna", "Bologna", 44.4938203, 11.3426327, 3),
        new City("Bruges", "Brugge", 51.2085526, 3.226772, 4),
        new City("Antalya", "Antalya", 36.8969, 30.7133, 5),
        new City("Sarajevo", "Sarajevo", 43.8570713, 18.4126147, 6),
        new City("Copenhagen", "Copenhagen", 55.6761, 12.5683, 7),
        new City("Portland Maine", "Portland", 43.6591, -70.2568, 8),
        new City("Portland Oregon", "Portland", 45.5152, -122.6784, 9));
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();
    static final Set<String> RESOLVED = ConcurrentHashMap.newKeySet();
    static final HttpServer SERVER = server();
    @Autowired MockMvc mvc;
    @Autowired PlaceRepository places;
    @DynamicPropertySource static void endpoints(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        r.add("journy.destinations.nominatim.enabled", () -> true);
        r.add("journy.destinations.nominatim.endpoint", () -> base + "/search");
        r.add("journy.places.osm.enabled", () -> true);
        r.add("journy.places.osm.endpoint", () -> base + "/interpreter");
    }
    @AfterAll static void stopServer() { SERVER.stop(0); }
    static Stream<City> cities() { return CITIES.stream(); }
    @ParameterizedTest(name = "{0}: discovery, identity, isolation, cache reuse")
    @MethodSource("cities")
    void unseededPipelineAndExploreStayIsolated(City city) throws Exception {
        assertThat(localPool(city)).isEmpty();
        Map<String, String> before = otherCities(city);
        String token = JSON.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("fullName", "Matrix", "email", UUID.randomUUID() + "@example.test", "password", "secret123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        for (int attempt = 0; attempt < 2; attempt++) {
            JsonNode trip = JSON.readTree(mvc.perform(post("/api/trips").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                    "destination", city.raw(), "startingArea", "", "startDate", "2026-10-10", "endDate", "2026-10-12",
                    "travelerType", "SOLO", "budget", "BALANCED", "pace", "BALANCED", "interests", List.of("CULTURE", "COFFEE")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(trip.get("destination").asText()).isEqualTo(city.locality());
            if (attempt == 1) mvc.perform(post("/api/trips/{id}/generate", trip.get("id").asText())
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            JsonNode itinerary = JSON.readTree(mvc.perform(get("/api/trips/{id}/itinerary", trip.get("id").asText())
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(localPool(city)).hasSize(12).allSatisfy(p -> {
                assertThat(p.getProviderFetchedAt()).isNotNull();
                assertThat(p.getProvider()).isEqualTo("osm");
                assertThat(Math.abs(p.getLatitude() - city.lat())).isLessThan(.002);
                assertThat(Math.abs(p.getLongitude() - city.lon())).isLessThan(.002);
            });
            Set<String> ids = new HashSet<>();
            for (JsonNode day : itinerary.get("days")) {
                assertThat(day.path("titleTranslations").path("tr").asText()).containsAnyOf("Kültür", "Kahve");
                for (JsonNode stop : day.get("stops")) {
                    assertThat(ids.add(stop.get("placeId").asText())).isTrue();
                    Place p = places.findById(stop.get("placeId").asText()).orElseThrow();
                    assertThat(p.getCity()).isEqualTo(city.locality());
                    assertThat(stop.get("title").asText()).isEqualTo(p.getName());
                    assertThat(stop.get("latitude").asDouble()).isEqualTo(p.getLatitude());
                    assertThat(stop.get("longitude").asDouble()).isEqualTo(p.getLongitude());
                    assertThat(stop.get("source").asText()).isEqualTo("provider:osm");
                }
            }
            assertThat(ids).hasSize(8);
            assertThat(CALLS.get(city.raw()).get()).isEqualTo(1);
        }
        JsonNode explore = JSON.readTree(mvc.perform(get("/api/explore/places").param("city", city.raw())
            .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(explore.isArray()).isTrue();
        assertThat(explore.size()).isEqualTo(12);
        for (JsonNode p : explore) {
            assertThat(p.get("city").asText()).isEqualTo(city.locality());
            assertThat(places.findById(p.get("id").asText())).isPresent();
        }
        assertThat(CALLS.get(city.raw()).get()).isEqualTo(1);
        assertThat(otherCities(city)).isEqualTo(before);
        if (!Set.of("Antalya", "Copenhagen").contains(city.raw())) assertThat(RESOLVED).contains(city.raw());
    }
    private List<Place> localPool(City city) {
        return places.findProviderCachedByCity(city.locality()).stream().filter(p -> Math.abs(p.getLatitude() - city.lat()) < .01 && Math.abs(p.getLongitude() - city.lon()) < .01).toList();
    }
    private Map<String, String> otherCities(City city) {
        Map<String, String> snapshot = new TreeMap<>();
        places.findAll().stream().filter(p -> !localPool(city).stream().anyMatch(local -> local.getId().equals(p.getId()))).forEach(p ->
            snapshot.put(p.getId(), p.getName() + "|" + p.getCity() + "|" + p.getLatitude() + "|" + p.getLongitude() + "|" + p.getProviderFetchedAt()));
        return snapshot;
    }
    static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/search", e -> {
                String query = URLDecoder.decode(e.getRequestURI().getRawQuery().split("&q=", 2)[1], StandardCharsets.UTF_8).replace('+', ' ');
                City c = CITIES.stream().filter(v -> v.raw().equals(query) || v.locality().equals(query)).findFirst().orElseThrow();
                RESOLVED.add(query);
                byte[] body = JSON.writeValueAsBytes(List.of(Map.of("lat", c.lat(), "lon", c.lon(), "osm_type", "relation", "osm_id", c.id(),
                    "display_name", c.locality(), "address", Map.of("city", c.locality(), "country", "Fixture country"))));
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); e.close();
            });
            server.createContext("/interpreter", e -> {
                String query = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var b = Pattern.compile("\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)").matcher(query);
                if (!b.find() || query.contains("around:") || !query.contains("[name]")) throw new IllegalStateException("Invalid bounded query");
                double lat = (Double.parseDouble(b.group(1)) + Double.parseDouble(b.group(3))) / 2;
                double lon = (Double.parseDouble(b.group(2)) + Double.parseDouble(b.group(4))) / 2;
                City c = CITIES.stream().filter(v -> Math.abs(v.lat() - lat) < .00001 && Math.abs(v.lon() - lon) < .00001).findFirst().orElseThrow();
                CALLS.computeIfAbsent(c.raw(), key -> new AtomicInteger()).incrementAndGet();
                List<Object> elements = new ArrayList<>();
                for (int i = 0; i < 12; i++) elements.add(Map.of("type", "node", "id", c.id() * 1000 + i,
                    "lat", c.lat() + i * .0001, "lon", c.lon() + i * .0001,
                    "tags", Map.of("name", "HTTP fixture " + c.id() + " venue " + i, i % 2 == 0 ? "tourism" : "amenity", i % 2 == 0 ? "museum" : "cafe")));
                elements.add(elements.get(0));
                elements.add(Map.of("type", "node", "id", c.id() * 1000 + 90, "lat", c.lat(), "lon", c.lon(), "tags", Map.of("tourism", "museum")));
                elements.add(Map.of("type", "node", "id", c.id() * 1000 + 91, "lat", c.lat() + 1, "lon", c.lon(), "tags", Map.of("name", "Wrong area", "tourism", "museum")));
                byte[] body = JSON.writeValueAsBytes(Map.of("elements", elements));
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); e.close();
            });
            server.start(); return server;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
}
