package com.journy.backend.itinerary;

import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationProvider;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.provider.ExternalPlaceCandidate;
import com.journy.backend.explore.provider.PlaceProvider;
import com.journy.backend.explore.provider.PlaceProviderService;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryGenerationService;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.trip.model.Trip;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import com.journy.backend.common.exception.InsufficientDestinationDataException;

class ItineraryGenerationServiceUnitTest {

    @Test
    void verifiedCandidatesBecomeItineraryStopsWithIdentityAndExactCoordinates() {
        InMemoryItineraryDayRepository dayRepository = new InMemoryItineraryDayRepository();
        InMemoryPlaceRepository placeRepository = new InMemoryPlaceRepository();
        List<Place> verifiedPlaces = List.of(
                verifiedPlace("osm_las_1", "Neon Museum", "Las Vegas", PlaceCategory.CULTURE, 36.1769, -115.1350, 4.9),
                verifiedPlace("osm_las_2", "Fremont Street Experience", "Las Vegas", PlaceCategory.WALKING, 36.1708, -115.1443, 4.8),
                verifiedPlace("osm_las_3", "PublicUs", "Las Vegas", PlaceCategory.COFFEE, 36.1669, -115.1412, 4.7),
                verifiedPlace("osm_las_4", "Arts District Food Stop", "Las Vegas", PlaceCategory.FOOD, 36.1598, -115.1537, 4.6)
        );
        ItineraryGenerationService service = service(dayRepository, placeRepository, fakePlaceProviderService(verifiedPlaces));

        Trip trip = trip("Las Vegas", 1);
        service.generateIfMissing(trip);

        List<ItineraryStop> stops = savedStops(dayRepository);
        assertThat(stops).hasSize(4);
        assertThat(stops).extracting(ItineraryStop::getTitle)
                .containsExactlyInAnyOrder("Neon Museum", "Fremont Street Experience", "PublicUs", "Arts District Food Stop");
        assertThat(stops).allSatisfy(stop -> {
            assertThat(stop.getPlaceId()).startsWith("osm_las_");
            assertThat(stop.getSource()).isEqualTo("provider:osm");
        });
        ItineraryStop neon = stopNamed(stops, "Neon Museum");
        assertThat(neon.getLatitude()).isEqualTo(36.1769);
        assertThat(neon.getLongitude()).isEqualTo(-115.1350);
    }

    @Test
    void legacyRepositoryCannotFillPartialVerifiedSupply() {
        var days = new InMemoryItineraryDayRepository();
        var repository = new InMemoryPlaceRepository();
        repository.saved.add(seedPlace("D", "Legacy filler", "Edinburgh", PlaceCategory.CULTURE));
        var candidates = List.of(verifiedPlace("A", "Calton Hill", "Edinburgh", PlaceCategory.WALKING, 55.95, -3.18, 4.9));
        assertThatThrownBy(() -> service(days, repository, fakePlaceProviderService(candidates)).generateIfMissing(trip("Edinburgh", 1)))
                .isInstanceOf(InsufficientDestinationDataException.class);
        assertThat(days.saved).isEmpty();
    }

    @Test
    void invalidProviderSelectionsProduceControlledFailure() {
        var days = new InMemoryItineraryDayRepository();
        Place invalid = seedPlace("D", "Unknown", "Tallinn", PlaceCategory.CULTURE);
        assertThatThrownBy(() -> service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(List.of(invalid)))
                .generateIfMissing(trip("Tallinn", 1))).isInstanceOf(InsufficientDestinationDataException.class);
        assertThat(days.saved).isEmpty();
    }

    @Test
    void zeroCandidatesFailsWithoutPersistingAnyDaysOrStops() {
        var days = new InMemoryItineraryDayRepository();
        assertThatThrownBy(() -> service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(List.of()))
                .generateIfMissing(trip("Bologna", 1)))
                .isInstanceOfSatisfying(InsufficientDestinationDataException.class, error -> {
                    assertThat(error.required()).isEqualTo(4); assertThat(error.available()).isZero();
                });
        assertThat(days.saved).isEmpty();
    }

    @Test
    void freshCachedCandidatesAreUsableWithoutCallingProvider() {
        InMemoryItineraryDayRepository dayRepository = new InMemoryItineraryDayRepository();
        InMemoryPlaceRepository placeRepository = new InMemoryPlaceRepository();
        placeRepository.saved.addAll(List.of(
                verifiedPlace("osm_cph_1", "Designmuseum Danmark", "Copenhagen", PlaceCategory.CULTURE, 55.6842, 12.5931, 4.9),
                verifiedPlace("osm_cph_2", "Nyhavn Waterfront", "Copenhagen", PlaceCategory.WALKING, 55.6797, 12.5910, 4.8),
                verifiedPlace("osm_cph_3", "Coffee Collective Bernikow", "Copenhagen", PlaceCategory.COFFEE, 55.6811, 12.5819, 4.7),
                verifiedPlace("osm_cph_4", "Torvehallerne", "Copenhagen", PlaceCategory.FOOD, 55.6838, 12.5719, 4.6)
        ));
        CountingPlaceProvider provider = new CountingPlaceProvider();
        PlaceProviderService placeProviderService = new PlaceProviderService(
                List.of(provider),
                placeRepository.proxy(),
                destinationResolutionService(),
                20,
                Duration.ofDays(1),
                2,
                2
        );
        ItineraryGenerationService service = service(dayRepository, placeRepository, placeProviderService);

        service.generateIfMissing(trip("Copenhagen", 1));

        assertThat(provider.calls).isZero();
        List<ItineraryStop> stops = savedStops(dayRepository);
        assertThat(stops).extracting(ItineraryStop::getTitle)
                .contains("Designmuseum Danmark", "Nyhavn Waterfront", "Coffee Collective Bernikow", "Torvehallerne");
        assertThat(stops).allSatisfy(stop -> assertThat(stop.getSource()).isEqualTo("provider:osm"));
        ItineraryStop nyhavn = stopNamed(stops, "Nyhavn Waterfront");
        assertThat(nyhavn.getLatitude()).isEqualTo(55.6797);
        assertThat(nyhavn.getLongitude()).isEqualTo(12.5910);
    }

    @Test
    void destinationsCannotLeakEvenIfProviderReturnsMixedPools() {
        List<Place> mixed = List.of(
                verifiedPlace("sarajevo_a", "Zmajevac", "Sarajevo", PlaceCategory.WALKING, 43.86, 18.44, 4.9),
                verifiedPlace("sarajevo_b", "Muzej Alije Izetbegovića", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.8),
                verifiedPlace("tallinn_a", "Maiasmokk", "Tallinn", PlaceCategory.COFFEE, 59.43, 24.74, 4.9),
                verifiedPlace("tallinn_b", "Russalka", "Tallinn", PlaceCategory.WALKING, 59.44, 24.79, 4.8),
                verifiedPlace("sarajevo_c", "Cafe", "Sarajevo", PlaceCategory.COFFEE, 43.85, 18.42, 4.7),
                verifiedPlace("sarajevo_d", "Food", "Sarajevo", PlaceCategory.FOOD, 43.85, 18.43, 4.6),
                verifiedPlace("tallinn_c", "Museum", "Tallinn", PlaceCategory.CULTURE, 59.43, 24.75, 4.7),
                verifiedPlace("tallinn_d", "Food", "Tallinn", PlaceCategory.FOOD, 59.44, 24.76, 4.6));
        for (String city : List.of("Sarajevo", "Tallinn")) {
            var days = new InMemoryItineraryDayRepository();
            service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(mixed)).generateIfMissing(trip(city, 1));
            var real = savedStops(days).stream().filter(stop -> stop.getSource().startsWith("provider:")).toList();
            assertThat(real).hasSize(4).allSatisfy(stop -> {
                Place selected = mixed.stream().filter(place -> place.getId().equals(stop.getPlaceId())).findFirst().orElseThrow();
                assertThat(selected.getCity()).isEqualTo(city);
                assertThat(stop.getTitle()).isEqualTo(selected.getName());
                assertThat(stop.getLatitude()).isEqualTo(selected.getLatitude());
                assertThat(stop.getLongitude()).isEqualTo(selected.getLongitude());
            });
        }
    }

    @Test
    void duplicateIdsDoNotInflateSupplyAndExhaustionFailsDeterministically() {
        var days = new InMemoryItineraryDayRepository();
        Place a = verifiedPlace("A", "Museum", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.9);
        Place b = verifiedPlace("B", "Cafe", "Sarajevo", PlaceCategory.COFFEE, 43.85, 18.42, 4.8);
        var generator = service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(List.of(a, a, b, b)));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> generator.generateIfMissing(trip("Sarajevo", 3)))
                    .isInstanceOfSatisfying(InsufficientDestinationDataException.class, error -> {
                        assertThat(error.required()).isEqualTo(12); assertThat(error.available()).isEqualTo(2);
                    });
        }
        assertThat(days.saved).isEmpty();
    }

    @Test
    void malformedProviderRowsCannotBecomeVerified() {
        var invalid = new ArrayList<Place>();
        for (int i = 0; i < 9; i++) invalid.add(verifiedPlace("bad" + i, "Invalid", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.9));
        invalid.get(0).setId(null);
        invalid.get(1).setProvider(" ");
        invalid.get(2).setProviderFetchedAt(null);
        invalid.get(3).setLatitude(null);
        invalid.get(4).setLongitude(Double.NaN);
        invalid.get(5).setLatitude(91.0);
        invalid.get(6).setCategory(null);
        invalid.get(7).setName("");
        invalid.get(8).setProvider("planned_fallback");
        var days = new InMemoryItineraryDayRepository();
        assertThatThrownBy(() -> service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(invalid))
                .generateIfMissing(trip("Sarajevo", 1))).isInstanceOf(InsufficientDestinationDataException.class);
        assertThat(days.saved).isEmpty();
    }

    @Test
    void unknownVerifiedRepositoryRowCannotBypassTheAcceptedProviderPool() {
        var days = new InMemoryItineraryDayRepository();
        var repository = new InMemoryPlaceRepository();
        repository.saved.add(verifiedPlace("D", "Not in candidate pool", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.9));
        assertThatThrownBy(() -> service(days, repository, fakePlaceProviderService(List.of()))
                .generateIfMissing(trip("Sarajevo", 1))).isInstanceOf(InsufficientDestinationDataException.class);
        assertThat(days.saved).isEmpty();
    }

    @Test
    void existingLegacyItineraryIsKeptAndMappedWithoutNewIdentityRequirements() {
        var days = new InMemoryItineraryDayRepository();
        Trip trip = trip("Sarajevo", 1);
        var historical = new ItineraryDay(trip, 1, "Historical title", "Historical summary", 2);
        historical.addStop(new ItineraryStop(1, "Historical stop", "CULTURE", "09:30", null, null, "", 43.8, 18.4));
        days.saved.add(historical);
        historical.addStop(new ItineraryStop(2, "Historical fallback", "WALKING", "11:30", null, "planned_fallback", "", 43.8, 18.4));
        var provider = fakePlaceProviderService(List.of());
        service(days, new InMemoryPlaceRepository(), provider).generateIfMissing(trip);
        var response = new com.journy.backend.itinerary.mapper.ItineraryMapper().toDayResponse(historical);
        assertThat(days.saved).containsExactly(historical);
        assertThat(response.title()).isEqualTo("Historical title");
        assertThat(response.titleTranslations()).isEmpty();
        assertThat(response.stops()).hasSize(2);
        assertThat(response.stops().getFirst()).satisfies(stop -> {
            assertThat(stop.placeId()).isNull();
            assertThat(stop.source()).isNull();
            assertThat(stop.title()).isEqualTo("Historical stop");
        });
    }

    @Test
    void staleUsableCacheSurvivesProviderFailureButEmptyCacheFails() {
        for (boolean cached : List.of(true, false)) {
            var days = new InMemoryItineraryDayRepository();
            var repository = new InMemoryPlaceRepository();
            if (cached) {
                for (int i = 0; i < 4; i++) {
                    Place place = verifiedPlace("stale" + i, "Cached " + i, "Copenhagen", PlaceCategory.CULTURE, 55.68, 12.59, 4.8);
                    place.setProviderFetchedAt(Instant.now().minus(Duration.ofDays(3)));
                    repository.saved.add(place);
                }
            }
            var provider = new CountingPlaceProvider();
            var loader = new PlaceProviderService(List.of(provider), repository.proxy(), destinationResolutionService(), 20, Duration.ofDays(1), 2, 2);
            var generator = service(days, repository, loader);
            if (cached) {
                generator.generateIfMissing(trip("Copenhagen", 1));
                assertThat(savedStops(days)).hasSize(4).allSatisfy(stop -> assertThat(stop.getSource()).isEqualTo("provider:osm"));
            } else {
                assertThatThrownBy(() -> generator.generateIfMissing(trip("Copenhagen", 1)))
                        .isInstanceOf(InsufficientDestinationDataException.class);
                assertThat(days.saved).isEmpty();
            }
            assertThat(provider.calls).isEqualTo(1);
        }
    }

    @Test
    void failedRegenerationKeepsExistingHistoricalRows() {
        var days = new InMemoryItineraryDayRepository();
        Trip trip = trip("Sarajevo", 1);
        var historical = new ItineraryDay(trip, 1, "Historical", "", 2);
        historical.addStop(new ItineraryStop(1, "Old Town Walk", "WALKING", "09:30", null, "planned_fallback", "", 43.8, 18.4));
        days.saved.add(historical);
        assertThatThrownBy(() -> service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(List.of())).regenerate(trip))
                .isInstanceOf(InsufficientDestinationDataException.class);
        assertThat(days.saved).containsExactly(historical);
    }

    private ItineraryGenerationService service(
            InMemoryItineraryDayRepository dayRepository,
            InMemoryPlaceRepository placeRepository,
            PlaceProviderService placeProviderService
    ) {
        return new ItineraryGenerationService(
                dayRepository.proxy(),
                placeProviderService
        );
    }

    private PlaceProviderService fakePlaceProviderService(List<Place> places) {
        return new PlaceProviderService(List.of(), new InMemoryPlaceRepository().proxy(), destinationResolutionService(), 20, Duration.ofDays(1), 10, 4) {
            @Override
            public List<Place> loadCityPlaces(String city, PlaceCategory category, boolean forYou, int limit) {
                return places.stream().limit(limit).toList();
            }
        };
    }

    private DestinationResolutionService destinationResolutionService() {
        return new DestinationResolutionService(List.of(), new DestinationCoordinateResolver(List.of()));
    }

    private Trip trip(String destination, int days) {
        return new Trip(
                null,
                destination,
                "Old town",
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 10).plusDays(days),
                TravelerType.COUPLE,
                BudgetMode.BALANCED,
                TripPace.BALANCED,
                Set.of(TravelInterest.COFFEE, TravelInterest.LOCAL_FOOD, TravelInterest.MUSEUMS, TravelInterest.WALKING)
        );
    }

    private Place verifiedPlace(String id, String name, String city, PlaceCategory category, double latitude, double longitude, double rating) {
        Place place = seedPlace(id, name, city, category);
        place.setProvider("osm");
        place.setProviderPlaceId(id);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        place.setRating(rating);
        place.setProviderFetchedAt(Instant.now());
        return place;
    }

    private Place seedPlace(String id, String name, String city, PlaceCategory category) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setCity(city);
        place.setCategory(category);
        place.setDescription(name + " description");
        place.setPriceLevel("Mid");
        place.setRating(4.5);
        place.setImageUrl("");
        place.setAddress(city);
        place.setProvider("seed");
        place.setLatitude(1.0);
        place.setLongitude(1.0);
        place.setOpeningHours("10:00 - 18:00");
        place.setEstimatedVisitMinutes(60);
        place.setTags(category.name().toLowerCase());
        return place;
    }

    private List<ItineraryStop> savedStops(InMemoryItineraryDayRepository dayRepository) {
        return dayRepository.saved.stream()
                .flatMap(day -> day.getStops().stream())
                .toList();
    }

    private ItineraryStop stopNamed(List<ItineraryStop> stops, String name) {
        return stops.stream()
                .filter(stop -> stop.getTitle().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static class InMemoryItineraryDayRepository {
        private final List<ItineraryDay> saved = new ArrayList<>();

        ItineraryDayRepository proxy() {
            return (ItineraryDayRepository) Proxy.newProxyInstance(
                    ItineraryDayRepository.class.getClassLoader(),
                    new Class<?>[]{ItineraryDayRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByTripIdOrderByDayNumberAsc")) {
                            return saved.stream()
                                    .filter(day -> day.getTrip() != null && Objects.equals(day.getTrip().getId(), args[0]))
                                    .toList();
                        }
                        if (method.getName().equals("saveAll")) {
                            Iterable<?> iterable = (Iterable<?>) args[0];
                            for (Object item : iterable) {
                                saved.add((ItineraryDay) item);
                            }
                            return args[0];
                        }
                        if (method.getName().equals("deleteByTripId")) {
                            saved.removeIf(day -> day.getTrip() != null && Objects.equals(day.getTrip().getId(), args[0]));
                            return null;
                        }
                        if (method.getName().equals("toString")) {
                            return "InMemoryItineraryDayRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class InMemoryPlaceRepository {
        private final List<Place> saved = new ArrayList<>();

        PlaceRepository proxy() {
            return (PlaceRepository) Proxy.newProxyInstance(
                    PlaceRepository.class.getClassLoader(),
                    new Class<?>[]{PlaceRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findAll")) {
                            return List.copyOf(saved);
                        }
                        if (method.getName().equals("findById")) {
                            return saved.stream().filter(place -> place.getId().equals(args[0])).findFirst();
                        }
                        if (method.getName().equals("findByProviderIgnoreCaseAndProviderPlaceId")) {
                            return saved.stream()
                                    .filter(place -> place.getProvider() != null && place.getProvider().equalsIgnoreCase((String) args[0]))
                                    .filter(place -> Objects.equals(place.getProviderPlaceId(), args[1]))
                                    .findFirst();
                        }
                        if (method.getName().equals("findProviderCachedByCity")) {
                            return providerCached((String) args[0], null, null);
                        }
                        if (method.getName().equals("findProviderCachedByCityAndCategory")) {
                            return providerCached((String) args[0], (PlaceCategory) args[1], null);
                        }
                        if (method.getName().equals("findFreshProviderCachedByCity")) {
                            return providerCached((String) args[0], null, (Instant) args[1]);
                        }
                        if (method.getName().equals("findFreshProviderCachedByCityAndCategory")) {
                            return providerCached((String) args[0], (PlaceCategory) args[1], (Instant) args[2]);
                        }
                        if (method.getName().equals("save")) {
                            Place place = (Place) args[0];
                            saved.removeIf(existing -> existing.getId().equals(place.getId()));
                            saved.add(place);
                            return place;
                        }
                        if (method.getName().equals("toString")) {
                            return "InMemoryPlaceRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private List<Place> providerCached(String city, PlaceCategory category, Instant cutoff) {
            return saved.stream()
                    .filter(place -> place.getCity().equalsIgnoreCase(city))
                    .filter(place -> category == null || place.getCategory() == category)
                    .filter(place -> place.getProvider() != null)
                    .filter(place -> !place.getProvider().equalsIgnoreCase("seed"))
                    .filter(place -> !place.getProvider().equalsIgnoreCase("starter"))
                    .filter(place -> place.getProviderFetchedAt() != null)
                    .filter(place -> cutoff == null || !place.getProviderFetchedAt().isBefore(cutoff))
                    .toList();
        }
    }

    private static class CountingPlaceProvider implements PlaceProvider {
        private int calls;

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            calls++;
            throw new IllegalStateException("provider unavailable");
        }
    }
}
