package com.journy.backend.itinerary;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.provider.ExternalPlaceCandidate;
import com.journy.backend.explore.provider.PlaceProvider;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryGenerationService;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.*;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"journy.places.osm.enabled=false", "journy.destinations.nominatim.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:phase9_cache;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@ActiveProfiles("test")
class PlannerCacheIntegrationTest {
    @Autowired PlaceRepository places;
    @Autowired TripRepository trips;
    @Autowired UserAccountRepository users;
    @Autowired ItineraryDayRepository days;
    @Autowired ItineraryGenerationService generator;
    @Autowired ItineraryMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CountingProvider provider;

    @Test
    void committedFreshCacheFeedsPlannerAndPersistedApiIdentityWithoutProviderRequests() {
        var user = users.save(new UserAccount("Phase 9", "phase9-cache@example.test", "unused", "Balanced"));
        Map<String, Place> pool = IntStream.range(0, 24).mapToObj(i -> {
            String city = i < 12 ? "Amsterdam" : "Copenhagen";
            var place = new Place("Cache Place " + i, city, i % 2 == 0 ? PlaceCategory.CULTURE : PlaceCategory.COFFEE, "", "Mid", 4, "");
            place.setId("phase9_" + i); place.setProvider("osm"); place.setProviderPlaceId("node/" + i);
            place.setProviderFetchedAt(Instant.now());
            place.setLatitude(i < 12 ? 52.37 + i * .0001 : 55.68 + i * .0001);
            place.setLongitude(i < 12 ? 4.9 + i * .0001 : 12.59 + i * .0001);
            return places.save(place);
        }).collect(Collectors.toMap(Place::getId, place -> place));
        var transaction = new TransactionTemplate(transactions);
        for (String city : List.of("Amsterdam", "Copenhagen")) {
            Trip trip = trips.save(new Trip(user, city, "", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12),
                    TravelerType.SOLO, BudgetMode.BALANCED, TripPace.BALANCED, Set.of(TravelInterest.CULTURE, TravelInterest.COFFEE)));
            transaction.executeWithoutResult(status -> generator.generateIfMissing(trips.findById(trip.getId()).orElseThrow()));
            transaction.executeWithoutResult(status -> {
                var response = mapper.toResponse(trip, days.findByTripIdOrderByDayNumberAsc(trip.getId()));
                var stops = response.days().stream().flatMap(day -> day.stops().stream()).toList();
                assertThat(stops).hasSize(8);
                assertThat(stops.stream().map(stop -> stop.placeId()).distinct().count()).isEqualTo(8);
                assertThat(stops).allSatisfy(stop -> {
                    Place selected = pool.get(stop.placeId());
                    assertThat(selected).isNotNull(); assertThat(selected.getCity()).isEqualTo(city);
                    assertThat(stop.source()).isEqualTo("provider:osm");
                    assertThat(stop.title()).isEqualTo(selected.getName());
                    assertThat(stop.latitude()).isEqualTo(selected.getLatitude());
                    assertThat(stop.longitude()).isEqualTo(selected.getLongitude());
                });
                assertThat(response.days()).allSatisfy(day -> assertThat(day.titleTranslations()).containsKeys("en", "tr"));
            });
        }
        assertThat(provider.calls.get()).isZero();
    }

    @TestConfiguration
    static class Configuration {
        @Bean CountingProvider countingProvider() { return new CountingProvider(); }
    }

    static class CountingProvider implements PlaceProvider {
        final AtomicInteger calls = new AtomicInteger();
        public String name() { return "counting"; }
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            calls.incrementAndGet();
            return List.of();
        }
    }
}
