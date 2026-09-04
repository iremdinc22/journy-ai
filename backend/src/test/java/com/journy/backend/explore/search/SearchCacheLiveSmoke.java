package com.journy.backend.explore.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.BackendApplication;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.service.PlannerPlaceContract;
import com.journy.backend.security.JwtService;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Opt-in test-classpath smoke: live OSM, then local fault injection; only an ephemeral H2 database. */
public class SearchCacheLiveSmoke {
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        var mapper = new ObjectMapper();
        var outage = new AtomicBoolean();
        var calls = new AtomicInteger();
        var onFailure = new AtomicReference<Runnable>(() -> {});
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        // Match the application's Apache transport (alternate DNS addresses), not JDK HttpClient's upstream selection.
        var factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setConnectionRequestTimeout(Duration.ofSeconds(6));
        factory.setReadTimeout(Duration.ofSeconds(35));
        var upstream = org.springframework.web.client.RestClient.builder().requestFactory(factory)
                .defaultHeader("User-Agent", "Journy/1.0 search-cache-acceptance").build();
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/osm", exchange -> {
            calls.incrementAndGet();
            byte[] request = exchange.getRequestBody().readAllBytes();
            int status = 504;
            byte[] body = "Test-only gateway timeout".getBytes(StandardCharsets.UTF_8);
            try {
                if (outage.get()) {
                    onFailure.getAndSet(() -> {}).run();
                } else {
                    var response = upstream.post().uri("https://overpass-api.de/api/interpreter")
                            .body(new String(request, StandardCharsets.UTF_8))
                            .exchange((requestInfo, result) -> org.springframework.http.ResponseEntity.status(result.getStatusCode())
                                    .body(result.getBody().readAllBytes()));
                    status = response.getStatusCode().value(); body = response.getBody();
                }
            } catch (Exception failure) {
                System.out.println("CACHE_SMOKE_PROXY_FAILURE " + failure.getClass().getSimpleName());
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        proxy.start();
        try (var context = new SpringApplication(BackendApplication.class).run(
                "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:search_cache_smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.sql.init.mode=never", "--journy.places.osm.enabled=true",
                "--journy.places.osm.endpoint=http://127.0.0.1:" + proxy.getAddress().getPort() + "/osm")) {
            var owner = context.getBean(UserAccountRepository.class)
                    .save(new UserAccount("Cache smoke", "cache-smoke@example.test", "unused", "Balanced traveler"));
            String token = context.getBean(JwtService.class).generateToken(owner);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            var places = context.getBean(PlaceRepository.class);
            var success = query(transport, port, token, "Selimiye");
            require(success.statusCode() == 200, "Live provider search status=" + success.statusCode());
            JsonNode expected = mapper.readTree(success.body());
            require(expected.isArray() && !expected.isEmpty(), "Live provider returned no matching records");
            boolean mosque = false;
            for (var row : expected) {
                require(places.findById(row.path("id").asText()).filter(PlannerPlaceContract::verified).isPresent(),
                        "Returned identity is not canonical");
                mosque |= row.path("providerPlaceId").asText().equals("relation/3376582")
                        && row.path("name").asText().equals("Selimiye Camii");
            }
            require(mosque, "Actual canonical Selimiye Camii was not returned");
            System.out.println("CACHE_SMOKE " + mapper.writeValueAsString(Map.of(
                    "stage", "live_provider_success", "status", 200, "results", expected)));

            // No fake places: only reuse the exact canonical records just fetched from live OSM.
            var recorded = places.findAll();
            places.deleteAll();
            outage.set(true);
            onFailure.set(() -> places.saveAllAndFlush(recorded));
            int beforeFailure = calls.get();
            var fallback = query(transport, port, token, "Selimiye");
            require(fallback.statusCode() == 200 && mapper.readTree(fallback.body()).equals(expected),
                    "Cache committed during provider failure was not returned unchanged");
            require(calls.get() > beforeFailure, "Fault injection did not execute");
            System.out.println("CACHE_SMOKE " + mapper.writeValueAsString(Map.of(
                    "stage", "injected_504_with_matching_cache", "status", 200, "exactIdentityPreserved", true)));

            int beforeHit = calls.get();
            var hit = query(transport, port, token, "Selimiye");
            require(hit.statusCode() == 200 && mapper.readTree(hit.body()).equals(expected), "Warm cache response changed");
            require(calls.get() == beforeHit, "Warm cache unnecessarily called provider");
            System.out.println("CACHE_SMOKE " + mapper.writeValueAsString(Map.of(
                    "stage", "cache_hit_provider_unavailable", "status", 200, "providerCalls", 0)));

            var unrelated = query(transport, port, token, "no-matching-place-acceptance");
            require(unrelated.statusCode() == 503, "Unrelated cache hid the provider failure");
            places.deleteAll();
            var empty = query(transport, port, token, "Selimiye");
            require(empty.statusCode() == 503 && places.count() == 0, "Cold failure fabricated a result");
            System.out.println("CACHE_SMOKE " + mapper.writeValueAsString(Map.of(
                    "stage", "unrelated_and_empty_cache", "unrelatedStatus", 503, "emptyStatus", 503,
                    "persistedPlaces", 0)));
        } finally { proxy.stop(0); factory.destroy(); }
    }
    private static HttpResponse<String> query(HttpClient client, int port, String token, String query) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/explore/places/search?city=Edirne&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
