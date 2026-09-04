package com.journy.backend.explore.search;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.provider.*;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.security.JwtService;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:search_reliability;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceSearchReliabilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlaceRepository places;
    @Autowired UserAccountRepository users;
    @Autowired JwtService jwt;
    @Autowired PlaceProviderService service;
    @MockitoBean OsmOverpassPlaceProvider provider;
    @MockitoBean DestinationResolutionService destinations;
    String token;
    static final ResolvedDestination ED = destination("Edirne", 41.6771, 26.5557);

    static ResolvedDestination destination(String city, double latitude, double longitude) {
        return new ResolvedDestination(city, city, city, null, null, latitude, longitude, "test", city);
    }
    @BeforeEach void setup() {
        places.deleteAll();
        token = jwt.generateToken(users.save(new UserAccount("Cache", UUID.randomUUID() + "@test.local", "unused", "Balanced traveler")));
        when(provider.name()).thenReturn("osm");
        when(destinations.resolve(anyString())).thenAnswer(call -> Optional.of(switch ((String) call.getArgument(0)) {
            case "Tallinn" -> destination("Tallinn", 59.437, 24.7536);
            case "Sarajevo" -> destination("Sarajevo", 43.8563, 18.4131);
            default -> ED;
        }));
        when(provider.searchPlaces(any(), any(), anyInt())).thenThrow(timeout());
    }
    HttpServerErrorException timeout() { return new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT); }
    Place cached(String id, String name, PlaceCategory category, String tags) {
        var p = new Place(name, "Edirne", category, "Provider record", "Mid", 0, "https://example.test/place");
        p.setId(id); p.setProvider("osm"); p.setProviderPlaceId("relation/" + id);
        p.setProviderFetchedAt(Instant.now().minusSeconds(172800)); // Stale verified cache is still usable.
        p.setLatitude(41.6771); p.setLongitude(26.5557); p.setTags(tags);
        return places.saveAndFlush(p);
    }
    ExternalPlaceCandidate discovered() {
        return new ExternalPlaceCandidate("osm", "relation/3376582", "Selimiye Camii", "Edirne",
                PlaceCategory.CULTURE, "Provider record", "Mid", 0, "https://example.test/place",
                null, null, 41.6771, 26.5557, null, 60, "culture,provider:osm");
    }
    org.springframework.test.web.servlet.ResultActions search(String city, String q) throws Exception {
        return mvc.perform(get("/api/explore/places/search").param("city", city).param("q", q)
                .header("Authorization", "Bearer " + token));
    }
    @Test void matchingCacheSurvivesProviderOutageWithoutProviderCallAndPreservesIdentity() throws Exception {
        var p = cached("osm_relation_3376582", "Selimiye Camii", PlaceCategory.CULTURE, "culture");
        search("Edirne", "selimiye").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(p.getId()))
                .andExpect(jsonPath("$[0].name").value(p.getName()))
                .andExpect(jsonPath("$[0].category").value("CULTURE"))
                .andExpect(jsonPath("$[0].latitude").value(p.getLatitude()))
                .andExpect(jsonPath("$[0].longitude").value(p.getLongitude()));
        verify(provider, never()).searchPlaces(any(), any(), anyInt());
    }
    @Test void noCacheAndTimeoutRemainExplicit503() throws Exception {
        search("Edirne", "Selimiye").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        assertThat(places.count()).isZero();
    }
    @Test void unrelatedCoffeeCacheCannotHideNameSearchFailure() throws Exception {
        cached("coffee", "Different cafe", PlaceCategory.COFFEE, "coffee");
        search("Edirne", "Selimiye").andExpect(status().isServiceUnavailable());
        assertThat(places.count()).isEqualTo(1);
    }
    @Test void cacheCommittedDuringFailedDiscoveryIsReturned() throws Exception {
        doAnswer(call -> {
            cached("late", "Selimiye Camii", PlaceCategory.CULTURE, "culture");
            throw timeout();
        }).when(provider).searchPlaces(any(), any(), anyInt());
        search("Edirne", "Selimiye").andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("late"));
        verify(provider).searchPlaces(any(), any(), anyInt());
    }
    @Test void failureThenSuccessThenCacheReuse() throws Exception {
        doThrow(timeout()).doReturn(List.of(discovered())).when(provider).searchPlaces(any(), any(), anyInt());
        search("Edirne", "Selimiye").andExpect(status().isServiceUnavailable());
        search("Edirne", "Selimiye").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("osm_relation_3376582"));
        search("Edirne", "Selimiye").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("osm_relation_3376582"));
        assertThat(places.findById("osm_relation_3376582")).isPresent();
        verify(provider, times(2)).searchPlaces(any(), any(), anyInt());
    }
    @ParameterizedTest @ValueSource(strings = {"Tallinn", "Sarajevo"})
    void citiesNeverShareUnrelatedCachedPlaces(String city) throws Exception {
        cached("selimiye", "Selimiye Camii", PlaceCategory.CULTURE, "culture");
        search(city, "Selimiye").andExpect(status().isServiceUnavailable());
    }
    @Test void identicalCityNamesStillRespectGeographicBoundary() throws Exception {
        cached("selimiye", "Selimiye Camii", PlaceCategory.CULTURE, "culture");
        when(destinations.resolve("Edirne")).thenReturn(Optional.of(destination("Edirne", 59.437, 24.7536)));
        search("Edirne", "Selimiye").andExpect(status().isServiceUnavailable());
    }
    @ParameterizedTest @ValueSource(strings = {"coffee", "museum"})
    void categoryCacheWorksDuringOutage(String query) throws Exception {
        var p = cached(query, "Canonical local place", query.equals("coffee") ? PlaceCategory.COFFEE : PlaceCategory.CULTURE,
                query.equals("museum") ? "culture,search-type:museum" : "coffee");
        search("Edirne", query).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(p.getId()));
        verify(provider, never()).searchPlaces(any(), any(), anyInt());
    }
    @Test void syntheticAndUnverifiedMatchesNeverBecomeFallback() throws Exception {
        for (String source : List.of("starter", "seed", "planned_fallback")) {
            var p = cached(source, "Selimiye", PlaceCategory.CULTURE, "culture");
            p.setProvider(source); places.saveAndFlush(p);
        }
        var p = cached("unverified", "Selimiye", PlaceCategory.CULTURE, "culture");
        p.setProviderFetchedAt(null); places.saveAndFlush(p);
        search("Edirne", "Selimiye").andExpect(status().isServiceUnavailable());
    }
    @Test void fallbackOrderRemainsExactPrefixSubstringThenNameAndId() throws Exception {
        cached("3", "Old Selimiye", PlaceCategory.CULTURE, "culture");
        cached("2", "Selimiye Camii", PlaceCategory.CULTURE, "culture");
        cached("1", "Selimiye", PlaceCategory.CULTURE, "culture");
        cached("0", "Selimiye", PlaceCategory.CULTURE, "culture");
        search("Edirne", "SELİMİYE").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("0")).andExpect(jsonPath("$[1].id").value("1"))
                .andExpect(jsonPath("$[2].id").value("2")).andExpect(jsonPath("$[3].id").value("3"));
        verify(provider, never()).searchPlaces(any(), any(), anyInt());
    }
    @Test void concurrentIdenticalCacheMissesShareOneDiscovery() throws Exception {
        concurrentDiscovery(false);
    }

    @Test void concurrentFailureRemains503AndLaterRetryCanRecover() throws Exception {
        concurrentDiscovery(true);
        doReturn(List.of(discovered())).when(provider).searchPlaces(any(), any(), anyInt());
        search("Edirne", "Selimiye").andExpect(status().isOk());
        verify(provider, times(2)).searchPlaces(any(), any(), anyInt());
    }

    private void concurrentDiscovery(boolean fail) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            calls.incrementAndGet(); entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Provider release timeout");
            if (fail) throw timeout();
            return List.of(discovered());
        }).when(provider).searchPlaces(any(), any(), anyInt());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> search("Edirne", "Selimiye"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var secondThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
            var second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                return search("Edirne", "Selimiye");
            });
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                        calls.get() > 1 || (secondThread.get() != null && Arrays.stream(secondThread.get().getStackTrace())
                                .anyMatch(frame -> frame.getClassName().equals(CompletableFuture.class.getName()))));
            } finally { release.countDown(); }
            int callsBeforeRelease = calls.get();
            first.get(10, TimeUnit.SECONDS).andExpect(fail ? status().isServiceUnavailable() : status().isOk());
            second.get(10, TimeUnit.SECONDS).andExpect(fail ? status().isServiceUnavailable() : status().isOk());
            assertThat(callsBeforeRelease).isEqualTo(1);
            assertThat(calls.get()).isEqualTo(1);
        } finally { release.countDown(); }
    }
}
