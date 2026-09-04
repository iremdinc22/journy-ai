package com.journy.backend.explore.search;

import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.feedback.repository.TasteFeedbackRepository;
import com.journy.backend.security.JwtService;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:place_search;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceSearchIntegrationTest {
    static final List<String> queries = new CopyOnWriteArrayList<>();
    static final HttpServer server = server();
    static String payload = "{\"elements\":[]}";
    static int providerStatus = 200;
    @Autowired MockMvc mvc;
    @Autowired PlaceRepository places;
    @Autowired UserAccountRepository users;
    @Autowired JwtService jwt;
    @Autowired TasteFeedbackRepository feedback;
    String token;

    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/osm", exchange -> {
                queries.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var bytes = payload.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(providerStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/destination", exchange -> {
                String q = URLDecoder.decode(exchange.getRequestURI().getQuery(), StandardCharsets.UTF_8);
                String city = q.substring(q.indexOf("&q=") + 3);
                double[] coordinates = coordinates(city);
                var bytes = ("[{\"osm_type\":\"relation\",\"osm_id\":\"test-destination\",\"lat\":" + coordinates[0]
                        + ",\"lon\":" + coordinates[1] + ",\"address\":{\"city\":\"" + city + "\",\"country\":\"Test\"}}]")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start(); return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry properties) {
        properties.add("journy.places.osm.enabled", () -> true);
        properties.add("journy.destinations.nominatim.enabled", () -> true);
        properties.add("journy.places.osm.endpoint", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/osm");
        properties.add("journy.destinations.nominatim.endpoint", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/destination");
    }
    @BeforeEach void setup() {
        places.deleteAll();
        queries.clear();
        payload = "{\"elements\":[]}";
        providerStatus = 200;
        token = jwt.generateToken(users.save(new UserAccount("Search", UUID.randomUUID() + "@test.local", "unused", "Balanced traveler")));
    }
    @AfterAll static void close() { server.stop(0); }
    static double[] coordinates(String city) {
        return switch (city) {
            case "Edirne" -> new double[]{41.6771, 26.5557};
            case "Tallinn" -> new double[]{59.437, 24.7536};
            case "Ghent" -> new double[]{51.0543, 3.7174};
            case "Brno" -> new double[]{49.1951, 16.6068};
            case "Ljubljana" -> new double[]{46.0569, 14.5058};
            default -> new double[]{46, 14};
        };
    }
    void fixture(String city, String name, String tags) {
        var c = coordinates(city);
        // Explicitly synthetic transport fixture IDs; live provider validation is separate.
        payload = "{\"elements\":[{\"type\":\"node\",\"id\":123456,\"lat\":" + c[0] + ",\"lon\":" + c[1]
                + ",\"tags\":{\"name\":\"" + name + "\"," + tags + "}}]}";
    }
    org.springframework.test.web.servlet.ResultActions search(String city, String q) throws Exception {
        return mvc.perform(get("/api/explore/places/search").param("city", city).param("q", q)
                .header("Authorization", "Bearer " + token));
    }
    @ParameterizedTest @CsvSource({"Edirne,Selimiye,Selimiye Camii", "Tallinn,Maiasmokk,Maiasmokk"})
    void namesUseProviderThenCanonicalCache(String city, String q, String name) throws Exception {
        fixture(city, name, city.equals("Edirne") ? "\"amenity\":\"place_of_worship\"" : "\"amenity\":\"cafe\"");
        if (city.equals("Edirne")) payload = payload.replace("\"type\":\"node\"", "\"type\":\"relation\"");
        search(city, q).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value(name))
                .andExpect(jsonPath("$[0].provider").value("osm"))
                .andExpect(jsonPath("$[0].id").value(city.equals("Edirne") ? "osm_relation_123456" : "osm_node_123456"));
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst()).contains("relation[");
        assertThat(queries.getFirst()).contains("[name~\"" + q.toLowerCase() + "\",i]");
        search(city, q.toUpperCase()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(queries).hasSize(1);
        assertThat(places.findAll()).allMatch(com.journy.backend.itinerary.service.PlannerPlaceContract::verified);
    }
    @ParameterizedTest @CsvSource({"Edirne", "Tallinn"})
    void coffeeIsCategorySearch(String city) throws Exception {
        fixture(city, "Provider cafe", "\"amenity\":\"cafe\"");
        search(city, "coffee").andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("COFFEE"));
        assertThat(queries.getFirst()).contains("\"amenity\"=\"cafe\"").doesNotContain("name~");
    }
    @ParameterizedTest @CsvSource({"Ghent,STAM", "Brno,Moravian Museum", "Ljubljana,National Museum of Slovenia"})
    void unseenDestinationDiscoversMuseums(String city, String name) throws Exception {
        assertThat(places.countByCityIgnoreCase(city)).isZero();
        fixture(city, name, "\"tourism\":\"museum\"");
        search(city, "museum").andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value(name))
                .andExpect(jsonPath("$[0].city").value(city)).andExpect(jsonPath("$[0].category").value("CULTURE"));
        assertThat(queries.getFirst()).contains("\"tourism\"=\"museum\"");
        assertThat(places.countByCityIgnoreCase(city)).isEqualTo(1);
        search(city, "müze").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(queries).hasSize(1);
    }
    @Test void successfulProviderZeroElementsReturnsHttp200EmptyWithoutSyntheticResults() throws Exception {
        search("Edirne", "Eiffel Tower").andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("[]"));
        assertThat(queries).hasSize(2); // Existing two-radius successful no-match search.
        assertThat(places.count()).isZero();
    }
    @ParameterizedTest @ValueSource(ints = {429, 503, 504})
    void providerHttpFailuresReturn503WithoutSyntheticResults(int statusCode) throws Exception {
        providerStatus = statusCode;
        search("Edirne", "Eiffel Tower").andExpect(status().isServiceUnavailable());
        assertThat(places.count()).isZero();
    }
    @Test void malformedProviderPayloadUsesExistingSafeEmptyBehavior() throws Exception {
        payload = "not-json";
        search("Edirne", "Eiffel Tower").andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(places.count()).isZero();
    }
    @Test void wayIdentityIsPreserved() throws Exception {
        fixture("Edirne", "Central Way", "\"tourism\":\"museum\"");
        payload = payload.replace("\"type\":\"node\"", "\"type\":\"way\"");
        search("Edirne", "Central").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("osm_way_123456"));
    }
    @Test void unrelatedCacheDoesNotSuppressNamedDiscoveryAndSearchWritesNoFeedback() throws Exception {
        var p = com.journy.backend.support.VerifiedPlaceFixtures.place("unrelated", "Other cafe", "Amsterdam",
                com.journy.backend.place.enums.PlaceCategory.COFFEE);
        p.setCity("Tallinn"); p.setLatitude(59.437); p.setLongitude(24.7536);
        places.save(p);
        long before = feedback.count();
        fixture("Tallinn", "Maiasmokk", "\"amenity\":\"cafe\"");
        search("Tallinn", "Maiasmokk").andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Maiasmokk"));
        assertThat(queries).hasSize(1);
        assertThat(feedback.count()).isEqualTo(before);
    }
    @Test void validationAndAuthenticationAreRequired() throws Exception {
        for (String q : List.of("", "a", "x".repeat(101), "a\nb")) search("Edirne", q).andExpect(status().isBadRequest());
        search("", "coffee").andExpect(status().isBadRequest());
        mvc.perform(get("/api/explore/places/search").param("city", "Tallinn").param("q", "coffee"))
                .andExpect(status().isForbidden());
        assertThat(queries).isEmpty();
    }
    @Test void literalRegexCharactersDoNotExpandTheQuery() throws Exception {
        search("Edirne", ".*").andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(queries.getFirst()).contains("name~\"\\\\.\\\\*\",i");
    }
}
