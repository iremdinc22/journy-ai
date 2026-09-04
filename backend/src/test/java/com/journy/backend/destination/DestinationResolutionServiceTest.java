package com.journy.backend.destination;

import com.journy.backend.destination.provider.DestinationCandidate;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationProvider;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.trip.dto.TripPreviewRequest;
import com.journy.backend.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationResolutionServiceTest {
    private static final double AMSTERDAM_LATITUDE = 52.3676;
    private static final double AMSTERDAM_LONGITUDE = 4.9041;

    @Test
    void resolvesKnownDestinationWithoutExternalProvider() {
        DestinationProvider unusedProvider = new EmptyProvider();
        DestinationResolutionService service = service(unusedProvider);

        Optional<ResolvedDestination> destination = service.resolve("Amsterdam");

        assertThat(destination).isPresent();
        assertThat(destination.get().locality()).isEqualTo("Amsterdam");
        assertThat(destination.get().country()).isEqualTo("Netherlands");
        assertThat(destination.get().latitude()).isEqualTo(52.3676);
        assertThat(destination.get().provider()).isEqualTo("known");
    }

    @Test
    void resolvesProviderBackedDestination() {
        DestinationProvider provider = new FakeProvider(new DestinationCandidate(
                "nominatim",
                "relation/170131",
                "Las Vegas",
                "United States",
                "Las Vegas, Clark County, Nevada, United States",
                36.1673,
                -115.1492
        ));
        DestinationResolutionService service = service(provider);

        Optional<ResolvedDestination> destination = service.resolve("Las Vegas");

        assertThat(destination).isPresent();
        assertThat(destination.get().originalQuery()).isEqualTo("Las Vegas");
        assertThat(destination.get().displayName()).contains("Las Vegas");
        assertThat(destination.get().locality()).isEqualTo("Las Vegas");
        assertThat(destination.get().country()).isEqualTo("United States");
        assertThat(destination.get().provider()).isEqualTo("nominatim");
        assertThat(destination.get().providerPlaceId()).isEqualTo("relation/170131");
    }

    @ParameterizedTest
    @MethodSource("providerBackedDestinations")
    void resolvesPreviouslyUnseenProviderBackedDestinations(
            String query,
            String locality,
            String country,
            double latitude,
            double longitude
    ) {
        DestinationResolutionService service = service(new MapProvider(Map.of(
                query,
                new DestinationCandidate(
                        "nominatim",
                        "place/" + query.toLowerCase().replace(" ", "-"),
                        locality,
                        country,
                        locality + ", " + country,
                        latitude,
                        longitude
                )
        )));

        Optional<ResolvedDestination> destination = service.resolve(query);

        assertThat(destination).isPresent();
        assertThat(destination.get().originalQuery()).isEqualTo(query);
        assertThat(destination.get().locality()).isEqualTo(locality);
        assertThat(destination.get().displayName()).contains(locality);
        assertThat(destination.get().country()).isEqualTo(country);
        assertThat(destination.get().provider()).isEqualTo("nominatim");
        assertThat(destination.get().providerPlaceId()).isNotBlank();
        assertValidNonDefaultCoordinates(destination.get(), latitude, longitude);
    }

    @ParameterizedTest
    @CsvSource({
            "Copenhagen,Copenhagen,Denmark,55.6761,12.5683",
            "Edinburgh,Edinburgh,United Kingdom,55.9533,-3.1883",
            "Antalya,Antalya,Turkey,36.8969,30.7133"
    })
    void resolvesExistingKnownDestinations(
            String query,
            String locality,
            String country,
            double latitude,
            double longitude
    ) {
        DestinationResolutionService service = service(new EmptyProvider());

        Optional<ResolvedDestination> destination = service.resolve(query);

        assertThat(destination).isPresent();
        assertThat(destination.get().originalQuery()).isEqualTo(query);
        assertThat(destination.get().locality()).isEqualTo(locality);
        assertThat(destination.get().country()).isEqualTo(country);
        assertThat(destination.get().provider()).isEqualTo("known");
        assertValidNonDefaultCoordinates(destination.get(), latitude, longitude);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsEmptyOrWhitespaceInput(String query) {
        DestinationResolutionService service = service(new EmptyProvider());

        Optional<ResolvedDestination> destination = service.resolve(query);

        assertThat(destination).isEmpty();
    }

    @Test
    void doesNotFallbackToAmsterdamWhenResolutionFails() {
        DestinationProvider failingProvider = new EmptyProvider();
        DestinationResolutionService service = service(failingProvider);

        Optional<ResolvedDestination> destination = service.resolve("Not A Real Journy City");

        assertThat(destination).isEmpty();
    }

    @Test
    void ignoresProviderResultsWithInvalidCoordinates() {
        DestinationProvider provider = new FakeProvider(new DestinationCandidate(
                "nominatim",
                "place/null-island",
                "Null Island",
                "Nowhere",
                "Null Island, Nowhere",
                0,
                0
        ));
        DestinationResolutionService service = service(provider);

        Optional<ResolvedDestination> destination = service.resolve("Null Island");

        assertThat(destination).isEmpty();
    }

    @Test
    void providerReturningNoResultFailsResolutionExplicitly() {
        DestinationResolutionService service = service(new EmptyProvider());

        Optional<ResolvedDestination> destination = service.resolve("DefinitelyNotARealDestinationName");

        assertThat(destination).isEmpty();
    }

    @Test
    void providerExceptionDoesNotFallbackToDefaultCoordinates() {
        DestinationResolutionService service = service(new ThrowingProvider());

        assertThatThrownBy(() -> service.resolve("Provider Failure City"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("provider failed");
    }

    @Test
    void tripPreviewRepresentsResolutionFailureAsBadRequest() {
        DestinationResolutionService resolutionService = service(new EmptyProvider());
        TripService tripService = new TripService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolutionService,
                null
        );

        assertThatThrownBy(() -> tripService.preview(new TripPreviewRequest(
                "DefinitelyNotARealDestinationName",
                null,
                null,
                null,
                null,
                null,
                null,
                "en"
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("Destination could not be resolved: DefinitelyNotARealDestinationName");
                });
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> providerBackedDestinations() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Las Vegas", "Las Vegas", "United States", 36.1673, -115.1492),
                org.junit.jupiter.params.provider.Arguments.of("Tallinn", "Tallinn", "Estonia", 59.4370, 24.7536),
                org.junit.jupiter.params.provider.Arguments.of("Osaka", "Osaka", "Japan", 34.6937, 135.5023),
                org.junit.jupiter.params.provider.Arguments.of("Bologna", "Bologna", "Italy", 44.4949, 11.3426),
                org.junit.jupiter.params.provider.Arguments.of("Bruges", "Bruges", "Belgium", 51.2093, 3.2247),
                org.junit.jupiter.params.provider.Arguments.of("Sarajevo", "Sarajevo", "Bosnia and Herzegovina", 43.8563, 18.4131)
        );
    }

    private void assertValidNonDefaultCoordinates(ResolvedDestination destination, double expectedLatitude, double expectedLongitude) {
        assertThat(destination.latitude()).isBetween(-90.0, 90.0);
        assertThat(destination.longitude()).isBetween(-180.0, 180.0);
        assertThat(destination.latitude()).isEqualTo(expectedLatitude);
        assertThat(destination.longitude()).isEqualTo(expectedLongitude);
        assertThat(destination.latitude()).isNotEqualTo(AMSTERDAM_LATITUDE);
        assertThat(destination.longitude()).isNotEqualTo(AMSTERDAM_LONGITUDE);
    }

    private DestinationResolutionService service(DestinationProvider provider) {
        DestinationCoordinateResolver coordinateResolver = new DestinationCoordinateResolver(List.of(provider));
        return new DestinationResolutionService(List.of(provider), coordinateResolver);
    }

    private record FakeProvider(DestinationCandidate candidate) implements DestinationProvider {
        @Override
        public String name() {
            return candidate.provider();
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            return Optional.of(candidate);
        }
    }

    private record MapProvider(Map<String, DestinationCandidate> candidates) implements DestinationProvider {
        @Override
        public String name() {
            return "map";
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            return Optional.ofNullable(candidates.get(query));
        }
    }

    private static class EmptyProvider implements DestinationProvider {
        @Override
        public String name() {
            return "empty";
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            return Optional.empty();
        }
    }

    private static class ThrowingProvider implements DestinationProvider {
        @Override
        public String name() {
            return "throwing";
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            throw new RuntimeException("provider failed");
        }
    }
}
