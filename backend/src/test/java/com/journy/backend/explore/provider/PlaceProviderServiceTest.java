package com.journy.backend.explore.provider;

import com.journy.backend.destination.provider.DestinationCandidate;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationImageResolver;
import com.journy.backend.destination.provider.DestinationProvider;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.mapper.PlaceMapper;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.explore.service.ExploreService;
import com.journy.backend.feedback.repository.TasteFeedbackRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceProviderServiceTest {

    @Test
    void discoversRealPlacesForResolvedUnseenDestination() {
        ResolvedDestination lasVegas = destination("Las Vegas", 36.1673, -115.1492);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", "node/1", "Bellagio Conservatory", "Las Vegas", PlaceCategory.CULTURE, 36.1128, -115.1767),
                candidate("osm", "node/2", "Arts District Coffee", "Las Vegas", PlaceCategory.COFFEE, 36.1609, -115.1539)
        )));

        List<ExternalPlaceCandidate> places = service.discover(lasVegas, null, 8);

        assertThat(places).hasSize(2);
        assertThat(places).extracting(ExternalPlaceCandidate::name)
                .containsExactly("Bellagio Conservatory", "Arts District Coffee");
        assertThat(places).allSatisfy(place -> {
            assertThat(place.city()).isEqualTo("Las Vegas");
            assertThat(place.provider()).isEqualTo("osm");
            assertThat(place.latitude()).isBetween(-90.0, 90.0);
            assertThat(place.longitude()).isBetween(-180.0, 180.0);
        });
    }

    @Test
    void preservesZeroProviderResults() {
        PlaceProviderService service = service(List.of(new StaticPlaceProvider()));

        List<ExternalPlaceCandidate> places = service.discover(destination("Tallinn", 59.4370, 24.7536), null, 8);

        assertThat(places).isEmpty();
    }

    @Test
    void filtersInvalidAndUnrelatedCoordinates() {
        ResolvedDestination osaka = destination("Osaka", 34.6937, 135.5023);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", "node/invalid", "Invalid Place", "Osaka", PlaceCategory.CULTURE, 0, 0),
                candidate("osm", "node/out-of-range", "Impossible Place", "Osaka", PlaceCategory.CULTURE, 91, 181),
                candidate("osm", "node/far", "Amsterdam Place", "Osaka", PlaceCategory.CULTURE, 52.3676, 4.9041),
                candidate("osm", "node/near", "Osaka Castle", "Osaka", PlaceCategory.CULTURE, 34.6873, 135.5262)
        )));

        List<ExternalPlaceCandidate> places = service.discover(osaka, null, 8);

        assertThat(places).singleElement().extracting(ExternalPlaceCandidate::name).isEqualTo("Osaka Castle");
    }

    @Test
    void filtersBlankNamePlaces() {
        ResolvedDestination edinburgh = destination("Edinburgh", 55.9533, -3.1883);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", "node/blank", " ", "Edinburgh", PlaceCategory.CULTURE, 55.9501, -3.1870),
                candidate("osm", "node/real", "Scott Monument", "Edinburgh", PlaceCategory.CULTURE, 55.9524, -3.1932)
        )));

        List<ExternalPlaceCandidate> places = service.discover(edinburgh, null, 8);

        assertThat(places).singleElement().extracting(ExternalPlaceCandidate::name).isEqualTo("Scott Monument");
    }

    @Test
    void removesProviderIdDuplicates() {
        ResolvedDestination bologna = destination("Bologna", 44.4949, 11.3426);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", "node/dupe", "Bologna Museum", "Bologna", PlaceCategory.CULTURE, 44.4971, 11.3434),
                candidate("osm", "node/dupe", "Bologna Museum Copy", "Bologna", PlaceCategory.CULTURE, 44.4972, 11.3435)
        )));

        List<ExternalPlaceCandidate> places = service.discover(bologna, null, 8);

        assertThat(places).singleElement().extracting(ExternalPlaceCandidate::name).isEqualTo("Bologna Museum");
    }

    @Test
    void removesNameAndCoordinateDuplicatesWhenProviderIdIsMissing() {
        ResolvedDestination tallinn = destination("Tallinn", 59.4370, 24.7536);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", null, "Tallinn Town Hall", "Tallinn", PlaceCategory.CULTURE, 59.4372, 24.7453),
                candidate("osm", "", "Tallinn Town Hall", "Tallinn", PlaceCategory.CULTURE, 59.43721, 24.74531),
                candidate("osm", null, "Kumu Art Museum", "Tallinn", PlaceCategory.CULTURE, 59.4360, 24.7967)
        )));

        List<ExternalPlaceCandidate> places = service.discover(tallinn, null, 8);

        assertThat(places).extracting(ExternalPlaceCandidate::name)
                .containsExactly("Tallinn Town Hall", "Kumu Art Museum");
    }

    @Test
    void keepsOnlyValidCandidatesFromMixedProviderResults() {
        ResolvedDestination antalya = destination("Antalya", 36.8969, 30.7133);
        PlaceProviderService service = service(List.of(placeProvider(
                candidate("osm", "node/blank", "", "Antalya", PlaceCategory.FOOD, 36.8871, 30.7062),
                candidate("osm", "node/zero", "Zero Cafe", "Antalya", PlaceCategory.COFFEE, 0, 0),
                candidate("osm", "node/far", "Remote Restaurant", "Antalya", PlaceCategory.FOOD, 41.0082, 28.9784),
                candidate("osm", "node/valid", "Hadrian Gate", "Antalya", PlaceCategory.CULTURE, 36.8851, 30.7082)
        )));

        List<ExternalPlaceCandidate> places = service.discover(antalya, null, 8);

        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("Hadrian Gate");
            assertThat(place.city()).isEqualTo("Antalya");
            assertThat(place.provider()).isEqualTo("osm");
        });
    }

    @Test
    void providerFailuresDoNotCreateFakePlacesAndDoNotStopOtherProviders() {
        ResolvedDestination bruges = destination("Bruges", 51.2093, 3.2247);
        PlaceProviderService service = service(List.of(
                new FailingPlaceProvider(),
                placeProvider(candidate("osm", "node/bruges", "Belfry of Bruges", "Bruges", PlaceCategory.CULTURE, 51.2081, 3.2243))
        ));

        List<ExternalPlaceCandidate> places = service.discover(bruges, null, 8);

        assertThat(places).singleElement().extracting(ExternalPlaceCandidate::name).isEqualTo("Belfry of Bruges");
    }

    @Test
    void knownDestinationUsesSameResolvedDiscoveryPipeline() {
        CapturingPlaceProvider provider = new CapturingPlaceProvider(
                candidate("osm", "node/cph", "Nyhavn", "Copenhagen", PlaceCategory.WALKING, 55.6797, 12.5910)
        );
        ResolvedDestination copenhagen = destination("Copenhagen", 55.6761, 12.5683);
        PlaceProviderService service = service(List.of(provider));

        List<ExternalPlaceCandidate> places = service.discover(copenhagen, null, 8);

        assertThat(provider.lastDestination).isEqualTo(copenhagen);
        assertThat(places).singleElement().extracting(ExternalPlaceCandidate::name).isEqualTo("Nyhavn");
    }

    @Test
    void enrichDestinationPersistsProviderPlacesWithoutSeedRows() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        PlaceProviderService service = service(
                List.of(placeProvider(candidate("osm", null, "National Gallery of Bosnia", "Sarajevo", PlaceCategory.CULTURE, 43.8587, 18.4231))),
                repository.proxy(),
                emptyDestinationResolutionService()
        );

        int saved = service.enrichDestination(destination("Sarajevo", 43.8563, 18.4131), null, 8);

        assertThat(saved).isEqualTo(1);
        assertThat(repository.saved).singleElement().satisfies(place -> {
            assertThat(place.getName()).isEqualTo("National Gallery of Bosnia");
            assertThat(place.getCity()).isEqualTo("Sarajevo");
            assertThat(place.getProvider()).isEqualTo("osm");
            assertThat(place.getProviderPlaceId()).isNull();
            assertThat(place.getId()).startsWith("osm_National_Gallery_of_Bosnia_");
        });
    }

    @Test
    void emptyDatabaseFirstRequestCallsProviderPersistsAndReturnsResults() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        CountingPlaceProvider provider = new CountingPlaceProvider(
                candidate("osm", "node/bascarsija", "Baščaršija", "Sarajevo", PlaceCategory.WALKING, 43.8590, 18.4310)
        );
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Sarajevo", 43.8563, 18.4131), null, true, 8);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.getName()).isEqualTo("Baščaršija");
            assertThat(place.getCity()).isEqualTo("Sarajevo");
            assertThat(place.getProvider()).isEqualTo("osm");
            assertThat(place.getProviderFetchedAt()).isNotNull();
        });
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void secondRequestReusesFreshCacheWithoutProviderOrDuplicates() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        CountingPlaceProvider provider = new CountingPlaceProvider(
                candidate("osm", "node/1", "Sarajevo City Hall", "Sarajevo", PlaceCategory.CULTURE, 43.8580, 18.4340),
                candidate("osm", "node/2", "Latin Bridge", "Sarajevo", PlaceCategory.CULTURE, 43.8570, 18.4280),
                candidate("osm", "node/3", "Gallery 11/07/95", "Sarajevo", PlaceCategory.CULTURE, 43.8595, 18.4250),
                candidate("osm", "node/4", "Museum of Sarajevo", "Sarajevo", PlaceCategory.CULTURE, 43.8591, 18.4301)
        );
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());
        ResolvedDestination sarajevo = destination("Sarajevo", 43.8563, 18.4131);

        List<Place> first = service.loadDestinationPlaces(sarajevo, PlaceCategory.CULTURE, false, 8);
        List<Place> second = service.loadDestinationPlaces(sarajevo, PlaceCategory.CULTURE, false, 8);

        assertThat(first).hasSize(4);
        assertThat(second).hasSize(4);
        assertThat(provider.calls).isEqualTo(1);
        assertThat(repository.saved).hasSize(4);
        assertThat(second).allSatisfy(place -> assertThat(place.getCity()).isEqualTo("Sarajevo"));
    }

    @Test
    void freshCacheReturnsCachedPlacesWithoutCallingProvider() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_node_cached", "Cafe Central", "Vienna", PlaceCategory.COFFEE, "osm", "node/cached", Instant.now()));
        repository.saved.add(cachedPlace("osm_node_cached_2", "Cafe Museum", "Vienna", PlaceCategory.COFFEE, "osm", "node/cached-2", Instant.now()));
        repository.saved.add(cachedPlace("osm_node_cached_3", "Kleines Cafe", "Vienna", PlaceCategory.COFFEE, "osm", "node/cached-3", Instant.now()));
        repository.saved.add(cachedPlace("osm_node_cached_4", "Jonas Reindl", "Vienna", PlaceCategory.COFFEE, "osm", "node/cached-4", Instant.now()));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/new", "New Cafe", "Vienna", PlaceCategory.COFFEE, 48.2100, 16.3660));
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Vienna", 48.2082, 16.3738), PlaceCategory.COFFEE, false, 4);

        assertThat(provider.calls).isZero();
        assertThat(places).extracting(Place::getName)
                .containsExactly("Cafe Central", "Cafe Museum", "Kleines Cafe", "Jonas Reindl");
    }

    @Test
    void ttlBoundaryTreatsInsideTtlAsFreshAndOutsideTtlAsStale() {
        Duration ttl = Duration.ofHours(2);
        InMemoryPlaceRepository freshRepository = new InMemoryPlaceRepository();
        freshRepository.saved.add(cachedPlace("osm_fresh_1", "Fresh One", "Bologna", PlaceCategory.COFFEE, "osm", "node/fresh-1", Instant.now().minus(ttl).plusSeconds(30)));
        freshRepository.saved.add(cachedPlace("osm_fresh_2", "Fresh Two", "Bologna", PlaceCategory.COFFEE, "osm", "node/fresh-2", Instant.now().minus(ttl).plusSeconds(30)));
        CountingPlaceProvider freshProvider = new CountingPlaceProvider(candidate("osm", "node/new", "New Bologna Cafe", "Bologna", PlaceCategory.COFFEE, 44.4960, 11.3430));
        PlaceProviderService freshService = service(List.of(freshProvider), freshRepository.proxy(), emptyDestinationResolutionService(), 20, ttl, 10, 2);

        freshService.loadDestinationPlaces(destination("Bologna", 44.4949, 11.3426), PlaceCategory.COFFEE, false, 4);

        assertThat(freshProvider.calls).isZero();

        InMemoryPlaceRepository staleRepository = new InMemoryPlaceRepository();
        staleRepository.saved.add(cachedPlace("osm_stale_1", "Stale One", "Bologna", PlaceCategory.COFFEE, "osm", "node/stale-1", Instant.now().minus(ttl).minusSeconds(30)));
        staleRepository.saved.add(cachedPlace("osm_stale_2", "Stale Two", "Bologna", PlaceCategory.COFFEE, "osm", "node/stale-2", Instant.now().minus(ttl).minusSeconds(30)));
        CountingPlaceProvider staleProvider = new CountingPlaceProvider(candidate("osm", "node/new", "New Bologna Cafe", "Bologna", PlaceCategory.COFFEE, 44.4960, 11.3430));
        PlaceProviderService staleService = service(List.of(staleProvider), staleRepository.proxy(), emptyDestinationResolutionService(), 20, ttl, 10, 2);

        staleService.loadDestinationPlaces(destination("Bologna", 44.4949, 11.3426), PlaceCategory.COFFEE, false, 4);

        assertThat(staleProvider.calls).isEqualTo(1);
    }

    @Test
    void staleCacheAttemptsRefreshAndUpdatesWithoutDuplicateGrowth() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_node_duomo", "Old Duomo Name", "Milan", PlaceCategory.CULTURE, "osm", "node/duomo", Instant.now().minus(Duration.ofDays(3))));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/duomo", "Duomo di Milano", "Milan", PlaceCategory.CULTURE, 45.4641, 9.1919));
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Milan", 45.4642, 9.1900), PlaceCategory.CULTURE, false, 4);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(repository.saved).hasSize(1);
        assertThat(places).singleElement().extracting(Place::getName).isEqualTo("Duomo di Milano");
        assertThat(repository.saved.get(0).getProviderFetchedAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void insufficientFreshCacheAttemptsProviderRefreshAndKeepsCachedData() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_node_one", "One Tallinn Cafe", "Tallinn", PlaceCategory.COFFEE, "osm", "node/one", Instant.now()));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/two", "Second Tallinn Cafe", "Tallinn", PlaceCategory.COFFEE, 59.4380, 24.7560));
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Tallinn", 59.4370, 24.7536), PlaceCategory.COFFEE, false, 8);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(places).extracting(Place::getName).contains("One Tallinn Cafe", "Second Tallinn Cafe");
    }

    @Test
    void insufficientForYouCacheAttemptsProviderRefresh() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_sarajevo_one", "Sarajevo One", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/one", Instant.now()));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/two", "Sarajevo Two", "Sarajevo", PlaceCategory.FOOD, 43.8570, 18.4140));
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService(), 20, Duration.ofDays(1), 2, 4);

        List<Place> places = service.loadDestinationPlaces(destination("Sarajevo", 43.8563, 18.4131), null, true, 8);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(places).extracting(Place::getName).contains("Sarajevo One", "Sarajevo Two");
    }

    @Test
    void configuredCacheMinimumChangesFreshCacheBehavior() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_one", "One Cafe", "Antalya", PlaceCategory.COFFEE, "osm", "node/one", Instant.now()));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/two", "Two Cafe", "Antalya", PlaceCategory.COFFEE, 36.8870, 30.7040));
        PlaceProviderService strictService = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService(), 20, Duration.ofDays(1), 10, 2);

        strictService.loadDestinationPlaces(destination("Antalya", 36.8866, 30.7030), PlaceCategory.COFFEE, false, 4);

        assertThat(provider.calls).isEqualTo(1);

        CountingPlaceProvider relaxedProvider = new CountingPlaceProvider(candidate("osm", "node/three", "Three Cafe", "Antalya", PlaceCategory.COFFEE, 36.8880, 30.7050));
        PlaceProviderService relaxedService = service(List.of(relaxedProvider), repository.proxy(), emptyDestinationResolutionService(), 20, Duration.ofDays(1), 10, 1);

        relaxedService.loadDestinationPlaces(destination("Antalya", 36.8866, 30.7030), PlaceCategory.COFFEE, false, 4);

        assertThat(relaxedProvider.calls).isZero();
    }

    @Test
    void configuredMaxDistanceControlsCandidateValidity() {
        ResolvedDestination bruges = destination("Bruges", 51.2093, 3.2247);
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/nearish", "Nearish Place", "Bruges", PlaceCategory.CULTURE, 51.2300, 3.2247));
        PlaceProviderService strictService = service(List.of(provider), new InMemoryPlaceRepository().proxy(), emptyDestinationResolutionService(), 1, Duration.ofDays(1), 10, 4);

        assertThat(strictService.discover(bruges, PlaceCategory.CULTURE, 4)).isEmpty();

        PlaceProviderService relaxedService = service(List.of(provider), new InMemoryPlaceRepository().proxy(), emptyDestinationResolutionService(), 5, Duration.ofDays(1), 10, 4);

        assertThat(relaxedService.discover(bruges, PlaceCategory.CULTURE, 4)).singleElement()
                .extracting(ExternalPlaceCandidate::name)
                .isEqualTo("Nearish Place");
    }

    @Test
    void providerFailureWithUsableStaleCacheReturnsStaleCacheWithoutFakePlaces() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_node_bridge", "Latin Bridge", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/bridge", Instant.now().minus(Duration.ofDays(5))));
        PlaceProviderService service = service(List.of(new FailingPlaceProvider()), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Sarajevo", 43.8563, 18.4131), PlaceCategory.CULTURE, false, 4);

        assertThat(places).singleElement().extracting(Place::getName).isEqualTo("Latin Bridge");
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void providerFailureWithoutCacheReturnsZero() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        PlaceProviderService service = service(List.of(new FailingPlaceProvider()), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Bruges", 51.2093, 3.2247), PlaceCategory.CULTURE, false, 4);

        assertThat(places).isEmpty();
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void legacySeededRowsWithoutFreshnessMetadataDoNotCrashCachePipeline() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("seed_paris", "Legacy Paris Walk", "Paris", PlaceCategory.WALKING, "seed", null, null));
        PlaceProviderService service = service(List.of(new FailingPlaceProvider()), repository.proxy(), emptyDestinationResolutionService());

        List<Place> places = service.loadDestinationPlaces(destination("Paris", 48.8566, 2.3522), PlaceCategory.WALKING, false, 4);

        assertThat(places).isEmpty();
        assertThat(repository.saved).singleElement().extracting(Place::getName).isEqualTo("Legacy Paris Walk");
    }

    @Test
    void repeatedRefreshDoesNotCreateDuplicateProviderRows() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_node_castle", "Old Castle", "Edinburgh", PlaceCategory.CULTURE, "osm", "node/castle", Instant.now().minus(Duration.ofDays(3))));
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/castle", "Edinburgh Castle", "Edinburgh", PlaceCategory.CULTURE, 55.9486, -3.1999));
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService());

        service.loadDestinationPlaces(destination("Edinburgh", 55.9533, -3.1883), PlaceCategory.CULTURE, false, 4);
        repository.saved.get(0).setProviderFetchedAt(Instant.now().minus(Duration.ofDays(3)));
        service.loadDestinationPlaces(destination("Edinburgh", 55.9533, -3.1883), PlaceCategory.CULTURE, false, 4);

        assertThat(provider.calls).isEqualTo(2);
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.get(0).getName()).isEqualTo("Edinburgh Castle");
    }

    @Test
    void repeatedRefreshWithoutProviderPlaceIdUsesNameAndCoordinatesForStableIdentity() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        CountingPlaceProvider provider = new CountingPlaceProvider(
                candidate("osm", null, "Central Cafe", "Bologna", PlaceCategory.COFFEE, 44.4938, 11.3426),
                candidate("osm", null, "Central Cafe", "Bologna", PlaceCategory.COFFEE, 44.5010, 11.3500)
        );
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService(), 20, Duration.ZERO, 10, 4);
        ResolvedDestination bologna = destination("Bologna", 44.4938, 11.3426);

        service.loadDestinationPlaces(bologna, PlaceCategory.COFFEE, false, 8);
        service.loadDestinationPlaces(bologna, PlaceCategory.COFFEE, false, 8);
        service.loadDestinationPlaces(bologna, PlaceCategory.COFFEE, false, 8);

        assertThat(provider.calls).isEqualTo(3);
        assertThat(repository.saved).hasSize(2);
    }

    @Test
    void destinationCachesRemainIsolatedByResolvedLocality() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        repository.saved.add(cachedPlace("osm_sarajevo_1", "Sarajevo Museum", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/sarajevo", Instant.now()));
        repository.saved.add(cachedPlace("osm_sarajevo_2", "Sarajevo Gallery", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/sarajevo-2", Instant.now()));
        repository.saved.add(cachedPlace("osm_sarajevo_3", "Sarajevo Hall", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/sarajevo-3", Instant.now()));
        repository.saved.add(cachedPlace("osm_sarajevo_4", "Sarajevo Bridge", "Sarajevo", PlaceCategory.CULTURE, "osm", "node/sarajevo-4", Instant.now()));
        CountingPlaceProvider provider = new CountingPlaceProvider(
                candidate("osm", "node/tallinn", "Tallinn Town Hall", "Tallinn", PlaceCategory.CULTURE, 59.4372, 24.7453),
                candidate("osm", "node/tallinn-2", "Kumu Art Museum", "Tallinn", PlaceCategory.CULTURE, 59.4360, 24.7967)
        );
        PlaceProviderService service = service(List.of(provider), repository.proxy(), emptyDestinationResolutionService(), 20, Duration.ofDays(1), 10, 4);

        List<Place> tallinn = service.loadDestinationPlaces(destination("Tallinn", 59.4370, 24.7536), PlaceCategory.CULTURE, false, 8);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(tallinn).extracting(Place::getCity).containsOnly("Tallinn");
        assertThat(repository.saved.stream().filter(place -> place.getCity().equals("Sarajevo")).count()).isEqualTo(4);
    }

    @Test
    void exploreConsumesProviderCacheAndCanTriggerDynamicDiscovery() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        CountingPlaceProvider provider = new CountingPlaceProvider(candidate("osm", "node/sarajevo", "Sarajevo City Hall", "Sarajevo", PlaceCategory.CULTURE, 43.8580, 18.4340));
        DestinationProvider destinationProvider = new StaticDestinationProvider(new DestinationCandidate(
                "nominatim",
                "place/sarajevo",
                "Sarajevo",
                "Bosnia and Herzegovina",
                "Sarajevo, Bosnia and Herzegovina",
                43.8563,
                18.4131
        ));
        DestinationResolutionService destinationResolutionService = new DestinationResolutionService(
                List.of(destinationProvider),
                new DestinationCoordinateResolver(List.of(destinationProvider))
        );
        PlaceProviderService cacheService = service(List.of(provider), repository.proxy(), destinationResolutionService);
        ExploreService exploreService = new ExploreService(
                repository.proxy(),
                new PlaceMapper(),
                cacheService,
                new DestinationImageResolver(RestClient.builder(), false),
                new DestinationCoordinateResolver(List.of(destinationProvider)),
                emptyFeedbackRepository(),
                currentUserService()
        );

        List<PlaceResponse> responses = exploreService.places("Culture", "Sarajevo");

        assertThat(provider.calls).isEqualTo(1);
        assertThat(repository.saved).singleElement().extracting(Place::getName).isEqualTo("Sarajevo City Hall");
        assertThat(responses).extracting(PlaceResponse::name).contains("Sarajevo City Hall");
    }

    @Test
    void enrichCityResolvesDestinationAndPersistsProviderPlaces() {
        InMemoryPlaceRepository repository = new InMemoryPlaceRepository();
        DestinationProvider destinationProvider = new StaticDestinationProvider(new DestinationCandidate(
                "nominatim",
                "place/sarajevo",
                "Sarajevo",
                "Bosnia and Herzegovina",
                "Sarajevo, Bosnia and Herzegovina",
                43.8563,
                18.4131
        ));
        PlaceProviderService service = service(
                List.of(placeProvider(candidate("osm", "node/1", "Sarajevo Museum", "Sarajevo", PlaceCategory.CULTURE, 43.8590, 18.4310))),
                repository.proxy(),
                new DestinationResolutionService(
                        List.of(destinationProvider),
                        new DestinationCoordinateResolver(List.of(destinationProvider))
                )
        );

        int saved = service.enrichCity("Sarajevo", null, 8);

        assertThat(saved).isEqualTo(1);
        assertThat(repository.saved).singleElement().satisfies(place -> {
            assertThat(place.getName()).isEqualTo("Sarajevo Museum");
            assertThat(place.getCity()).isEqualTo("Sarajevo");
            assertThat(place.getProvider()).isEqualTo("osm");
            assertThat(place.getProviderPlaceId()).isEqualTo("node/1");
            assertThat(place.getLatitude()).isEqualTo(43.8590);
            assertThat(place.getLongitude()).isEqualTo(18.4310);
        });
    }

    private PlaceProviderService service(List<PlaceProvider> providers) {
        return service(providers, new InMemoryPlaceRepository().proxy(), emptyDestinationResolutionService());
    }

    private DestinationResolutionService emptyDestinationResolutionService() {
        DestinationProvider emptyProvider = new EmptyDestinationProvider();
        return new DestinationResolutionService(List.of(emptyProvider), new DestinationCoordinateResolver(List.of(emptyProvider)));
    }

    private PlaceProviderService service(
            List<PlaceProvider> providers,
            PlaceRepository repository,
            DestinationResolutionService destinationResolutionService
    ) {
        return new PlaceProviderService(providers, repository, destinationResolutionService, 20, Duration.ofDays(1), 10, 4);
    }

    private PlaceProviderService service(
            List<PlaceProvider> providers,
            PlaceRepository repository,
            DestinationResolutionService destinationResolutionService,
            double maxDistanceKm,
            Duration cacheTtl,
            int cacheMinimumForYou,
            int cacheMinimumCategory
    ) {
        return new PlaceProviderService(
                providers,
                repository,
                destinationResolutionService,
                maxDistanceKm,
                cacheTtl,
                cacheMinimumForYou,
                cacheMinimumCategory
        );
    }

    private ResolvedDestination destination(String locality, double latitude, double longitude) {
        return new ResolvedDestination(locality, locality + ", Test", locality, null, "Test", latitude, longitude, "test", "place/" + locality);
    }

    private PlaceProvider placeProvider(ExternalPlaceCandidate... candidates) {
        return new StaticPlaceProvider(candidates);
    }

    private ExternalPlaceCandidate candidate(
            String provider,
            String providerPlaceId,
            String name,
            String city,
            PlaceCategory category,
            double latitude,
            double longitude
    ) {
        return new ExternalPlaceCandidate(
                provider,
                providerPlaceId,
                name,
                city,
                category,
                "Provider place",
                "Unknown",
                0,
                "",
                city,
                null,
                latitude,
                longitude,
                null,
                60,
                "provider:test"
        );
    }

    private Place cachedPlace(
            String id,
            String name,
            String city,
            PlaceCategory category,
            String provider,
            String providerPlaceId,
            Instant providerFetchedAt
    ) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setCity(city);
        place.setCategory(category);
        place.setDescription("Cached provider place");
        place.setPriceLevel("Mid");
        place.setRating(0);
        place.setImageUrl("");
        place.setAddress(city);
        place.setProvider(provider);
        place.setProviderPlaceId(providerPlaceId);
        place.setLatitude(switch (city) {
            case "Vienna" -> 48.2082;
            case "Milan" -> 45.4642;
            case "Tallinn" -> 59.4370;
            case "Sarajevo" -> 43.8563;
            case "Edinburgh" -> 55.9533;
            case "Bologna" -> 44.4949;
            case "Antalya" -> 36.8866;
            default -> 48.8566;
        });
        place.setLongitude(switch (city) {
            case "Vienna" -> 16.3738;
            case "Milan" -> 9.1900;
            case "Tallinn" -> 24.7536;
            case "Sarajevo" -> 18.4131;
            case "Edinburgh" -> -3.1883;
            case "Bologna" -> 11.3426;
            case "Antalya" -> 30.7030;
            default -> 2.3522;
        });
        place.setOpeningHours("10:00 - 18:00");
        place.setEstimatedVisitMinutes(60);
        place.setTags("cached");
        place.setProviderFetchedAt(providerFetchedAt);
        return place;
    }

    private static class InMemoryPlaceRepository {
        private final List<Place> saved = new ArrayList<>();

        PlaceRepository proxy() {
            return (PlaceRepository) Proxy.newProxyInstance(
                    PlaceRepository.class.getClassLoader(),
                    new Class<?>[]{PlaceRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByProviderIgnoreCaseAndProviderPlaceId")) {
                            return saved.stream()
                                    .filter(place -> place.getProvider().equalsIgnoreCase((String) args[0])
                                            && Objects.equals(place.getProviderPlaceId(), args[1]))
                                    .findFirst();
                        }
                        if (method.getName().equals("findByNameIgnoreCaseAndCityIgnoreCase")) {
                            return saved.stream()
                                    .filter(place -> place.getName().equalsIgnoreCase((String) args[0])
                                            && place.getCity().equalsIgnoreCase((String) args[1]))
                                    .findFirst();
                        }
                        if (method.getName().equals("findById")) {
                            return saved.stream()
                                    .filter(place -> place.getId().equals(args[0]))
                                    .findFirst();
                        }
                        if (method.getName().equals("findByCityIgnoreCaseOrderByRatingDesc")) {
                            return saved.stream()
                                    .filter(place -> place.getCity().equalsIgnoreCase((String) args[0]))
                                    .toList();
                        }
                        if (method.getName().equals("findByCityIgnoreCaseAndCategoryOrderByRatingDesc")) {
                            return saved.stream()
                                    .filter(place -> place.getCity().equalsIgnoreCase((String) args[0]))
                                    .filter(place -> place.getCategory() == args[1])
                                    .toList();
                        }
                        if (method.getName().equals("save")) {
                            Place place = (Place) args[0];
                            saved.removeIf(existing -> existing.getId().equals(place.getId()));
                            saved.add(place);
                            return place;
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

    private static class StaticPlaceProvider implements PlaceProvider {
        private final List<ExternalPlaceCandidate> candidates;

        StaticPlaceProvider(ExternalPlaceCandidate... candidates) {
            this.candidates = List.of(candidates);
        }

        @Override
        public String name() {
            return "static";
        }

        @Override
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            return candidates;
        }
    }

    private static class CapturingPlaceProvider extends StaticPlaceProvider {
        private ResolvedDestination lastDestination;

        CapturingPlaceProvider(ExternalPlaceCandidate... candidates) {
            super(candidates);
        }

        @Override
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            this.lastDestination = destination;
            return super.search(destination, category, limit);
        }
    }

    private static class CountingPlaceProvider extends StaticPlaceProvider {
        private int calls;

        CountingPlaceProvider(ExternalPlaceCandidate... candidates) {
            super(candidates);
        }

        @Override
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            calls++;
            return super.search(destination, category, limit);
        }
    }

    private static class FailingPlaceProvider implements PlaceProvider {
        @Override
        public String name() {
            return "failing";
        }

        @Override
        public List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit) {
            throw new IllegalStateException("provider unavailable");
        }
    }

    private TasteFeedbackRepository emptyFeedbackRepository() {
        return (TasteFeedbackRepository) Proxy.newProxyInstance(
                TasteFeedbackRepository.class.getClassLoader(),
                new Class<?>[]{TasteFeedbackRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc")) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private CurrentUserService currentUserService() {
        return new CurrentUserService(null) {
            @Override
            public UserAccount currentUser() {
                return new UserAccount("Test User", "test@example.com", "hash", "balanced");
            }
        };
    }

    private record StaticDestinationProvider(DestinationCandidate candidate) implements DestinationProvider {
        @Override
        public String name() {
            return candidate.provider();
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            return Optional.of(candidate);
        }
    }

    private static class EmptyDestinationProvider implements DestinationProvider {
        @Override
        public String name() {
            return "empty";
        }

        @Override
        public Optional<DestinationCandidate> resolve(String query) {
            return Optional.empty();
        }
    }
}
