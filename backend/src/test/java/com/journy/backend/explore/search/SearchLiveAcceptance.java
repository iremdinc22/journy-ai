package com.journy.backend.explore.search;

import com.journy.backend.BackendApplication;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import com.journy.backend.security.JwtService;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.service.PlannerPlaceContract;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Explicit, opt-in live API check. Test classpath only; isolated ephemeral H2 and random local port. */
public class SearchLiveAcceptance {
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        try (var context = new SpringApplication(BackendApplication.class).run(
                "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:search_live;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.sql.init.mode=never", "--journy.places.osm.enabled=true",
                "--journy.places.osm.endpoint=" + (args.length > 0 ? args[0] : "https://overpass-api.de/api/interpreter"),
                "--journy.destinations.nominatim.enabled=true")) {
            var owner = context.getBean(UserAccountRepository.class)
                    .save(new UserAccount("Live acceptance", "search-live@example.test", "unused", "Balanced traveler"));
            String token = context.getBean(JwtService.class).generateToken(owner);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            var client = RestClient.builder().baseUrl("http://127.0.0.1:" + port)
                    .defaultHeader("Authorization", "Bearer " + token).build();
            var mapper = context.getBean(ObjectMapper.class);
            var places = context.getBean(PlaceRepository.class);
            for (String[] scenario : new String[][]{
                    {"Edirne", "Selimiye"}, {"Tallinn", "Maiasmokk"}, {"Edirne", "coffee"},
                    {"Tallinn", "coffee"}, {"Ghent", "museum"}, {"Brno", "museum"}, {"Ljubljana", "museum"}}) {
                if (args.length > 1 && !Arrays.asList(args[1].split(",")).contains(scenario[0] + "/" + scenario[1])) continue;
                Thread.sleep(2000); // Respect public provider capacity; this launcher is opt-in.
                try {
                    long before = places.count();
                    String body = client.get().uri(builder -> builder.path("/api/explore/places/search")
                            .queryParam("city", scenario[0]).queryParam("q", scenario[1]).build())
                            .retrieve().body(String.class);
                    var rows = mapper.readTree(body);
                    boolean verified = rows.isArray() && !rows.isEmpty();
                    var names = new ArrayList<String>();
                    for (var row : rows) {
                        verified &= places.findById(row.path("id").asText()).filter(PlannerPlaceContract::verified).isPresent();
                        names.add(row.path("name").asText() + " [" + row.path("providerPlaceId").asText() + "]");
                    }
                    System.out.println("LIVE_SEARCH " + mapper.writeValueAsString(Map.of(
                            "city", scenario[0], "q", scenario[1], "verified", verified,
                            "recordsBefore", before, "count", rows.size(), "places", names)));
                } catch (Exception failure) {
                    System.out.println("LIVE_SEARCH " + mapper.writeValueAsString(Map.of(
                            "city", scenario[0], "q", scenario[1], "verified", false, "error", failure.toString())));
                }
            }
        }
    }
}
