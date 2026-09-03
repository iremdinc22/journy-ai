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

import static org.assertj.core.api.Assertions.assertThat;

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
    void repositoryPlacesAreNotMixedInWhenVerifiedCandidatesExist() {
        InMemoryItineraryDayRepository dayRepository = new InMemoryItineraryDayRepository();
        InMemoryPlaceRepository placeRepository = new InMemoryPlaceRepository();
        placeRepository.saved.add(seedPlace("repo_d", "Unverified Repo D", "Edinburgh", PlaceCategory.CULTURE));
        List<Place> verifiedPlaces = List.of(
                verifiedPlace("osm_a", "Calton Hill", "Edinburgh", PlaceCategory.WALKING, 55.9555, -3.1828, 4.9),
                verifiedPlace("osm_c", "The Milkman", "Edinburgh", PlaceCategory.COFFEE, 55.9508, -3.1888, 4.8)
        );
        ItineraryGenerationService service = service(dayRepository, placeRepository, fakePlaceProviderService(verifiedPlaces));

        service.generateIfMissing(trip("Edinburgh", 1));

        List<ItineraryStop> stops = savedStops(dayRepository);
        assertThat(stops).extracting(ItineraryStop::getTitle)
                .contains("Calton Hill", "The Milkman")
                .doesNotContain("Unverified Repo D");
        assertThat(stops.stream().filter(stop -> stop.getSource().startsWith("provider:"))).hasSize(2);
        assertThat(stops.stream().filter(stop -> stop.getSource().equals("planned_fallback"))).hasSize(2);
    }

    @Test
    void invalidProviderSelectionsAreIgnoredAndDoNotBecomeVerifiedStops() {
        InMemoryItineraryDayRepository dayRepository = new InMemoryItineraryDayRepository();
        InMemoryPlaceRepository placeRepository = new InMemoryPlaceRepository();
        Place invalid = seedPlace("not_verified", "Unknown Planner Choice", "Tallinn", PlaceCategory.CULTURE);
        invalid.setProvider(null);
        invalid.setProviderFetchedAt(null);
        ItineraryGenerationService service = service(dayRepository, placeRepository, fakePlaceProviderService(List.of(invalid)));

        service.generateIfMissing(trip("Tallinn", 1));

        List<ItineraryStop> stops = savedStops(dayRepository);
        assertThat(stops).extracting(ItineraryStop::getTitle).doesNotContain("Unknown Planner Choice");
        assertThat(stops).allSatisfy(stop -> {
            assertThat(stop.getPlaceId()).isNull();
            assertThat(stop.getSource()).isEqualTo("planned_fallback");
        });
    }

    @Test
    void noVerifiedCandidatesKeepsExistingPlannedFallbackBehavior() {
        InMemoryItineraryDayRepository dayRepository = new InMemoryItineraryDayRepository();
        InMemoryPlaceRepository placeRepository = new InMemoryPlaceRepository();
        ItineraryGenerationService service = service(dayRepository, placeRepository, fakePlaceProviderService(List.of()));

        service.generateIfMissing(trip("Bologna", 1));

        List<ItineraryStop> stops = savedStops(dayRepository);
        assertThat(stops).hasSize(4);
        assertThat(stops).allSatisfy(stop -> {
            assertThat(stop.getPlaceId()).isNull();
            assertThat(stop.getSource()).isEqualTo("planned_fallback");
            assertThat(stop.getLatitude()).isNotZero();
            assertThat(stop.getLongitude()).isNotZero();
        });
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
                verifiedPlace("tallinn_b", "Russalka", "Tallinn", PlaceCategory.WALKING, 59.44, 24.79, 4.8));
        for (String city : List.of("Sarajevo", "Tallinn")) {
            var days = new InMemoryItineraryDayRepository();
            service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(mixed)).generateIfMissing(trip(city, 2));
            var real = savedStops(days).stream().filter(stop -> stop.getSource().startsWith("provider:")).toList();
            assertThat(real).hasSize(2).allSatisfy(stop -> {
                Place selected = mixed.stream().filter(place -> place.getId().equals(stop.getPlaceId())).findFirst().orElseThrow();
                assertThat(selected.getCity()).isEqualTo(city);
                assertThat(stop.getTitle()).isEqualTo(selected.getName());
                assertThat(stop.getLatitude()).isEqualTo(selected.getLatitude());
                assertThat(stop.getLongitude()).isEqualTo(selected.getLongitude());
            });
        }
    }

    @Test
    void duplicateIdsAreUsedOnceAcrossTheWholeItineraryAndExhaustionStaysUnverified() {
        var days = new InMemoryItineraryDayRepository();
        Place a = verifiedPlace("A", "Museum", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.9);
        Place b = verifiedPlace("B", "Cafe", "Sarajevo", PlaceCategory.COFFEE, 43.85, 18.42, 4.8);
        service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(List.of(a, a, b, b)))
                .generateIfMissing(trip("Sarajevo", 3));
        var stops = savedStops(days);
        assertThat(stops).hasSize(12);
        assertThat(stops.stream().filter(stop -> stop.getSource().startsWith("provider:")))
                .extracting(ItineraryStop::getPlaceId).containsExactlyInAnyOrder("A", "B");
        assertThat(stops.stream().filter(stop -> stop.getSource().equals("planned_fallback")))
                .hasSize(10).allSatisfy(stop -> assertThat(stop.getPlaceId()).isNull());
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
        service(days, new InMemoryPlaceRepository(), fakePlaceProviderService(invalid)).generateIfMissing(trip("Sarajevo", 1));
        assertThat(savedStops(days)).allSatisfy(stop -> {
            assertThat(stop.getSource()).isEqualTo("planned_fallback");
            assertThat(stop.getPlaceId()).isNull();
        });
    }

    @Test
    void unknownVerifiedRepositoryRowCannotBypassTheAcceptedProviderPool() {
        var days = new InMemoryItineraryDayRepository();
        var repository = new InMemoryPlaceRepository();
        repository.saved.add(verifiedPlace("D", "Not in candidate pool", "Sarajevo", PlaceCategory.CULTURE, 43.86, 18.43, 4.9));
        service(days, repository, fakePlaceProviderService(List.of())).generateIfMissing(trip("Sarajevo", 1));
        assertThat(savedStops(days)).allSatisfy(stop -> {
            assertThat(stop.getPlaceId()).isNull();
            assertThat(stop.getSource()).isEqualTo("planned_fallback");
        });
    }

    @Test
    void existingLegacyItineraryIsKeptAndMappedWithoutNewIdentityRequirements() {
        var days = new InMemoryItineraryDayRepository();
        Trip trip = trip("Sarajevo", 1);
        var historical = new ItineraryDay(trip, 1, "Historical title", "Historical summary", 2);
        historical.addStop(new ItineraryStop(1, "Historical stop", "CULTURE", "09:30", null, null, "", 43.8, 18.4));
        days.saved.add(historical);
        var provider = fakePlaceProviderService(List.of());
        service(days, new InMemoryPlaceRepository(), provider).generateIfMissing(trip);
        var response = new com.journy.backend.itinerary.mapper.ItineraryMapper().toDayResponse(historical);
        assertThat(days.saved).containsExactly(historical);
        assertThat(response.title()).isEqualTo("Historical title");
        assertThat(response.titleTranslations()).isEmpty();
        assertThat(response.stops()).singleElement().satisfies(stop -> {
            assertThat(stop.placeId()).isNull();
            assertThat(stop.source()).isNull();
            assertThat(stop.title()).isEqualTo("Historical stop");
        });
    }

    private ItineraryGenerationService service(
            InMemoryItineraryDayRepository dayRepository,
            InMemoryPlaceRepository placeRepository,
            PlaceProviderService placeProviderService
    ) {
        return new ItineraryGenerationService(
                dayRepository.proxy(),
                placeRepository.proxy(),
                new DestinationCoordinateResolver(List.of()),
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
            return List.of();
        }
    }
}
