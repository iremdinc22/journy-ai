package com.journy.backend.explore.provider;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.place.enums.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaceProviderService {
    private static final Logger log = LoggerFactory.getLogger(PlaceProviderService.class);

    private final List<PlaceProvider> providers;
    private final PlaceRepository placeRepository;
    private final DestinationResolutionService destinationResolutionService;
    private final double maxDistanceKm;
    private final Duration cacheTtl;
    private final int cacheMinimumForYou;
    private final int cacheMinimumCategory;
    private final Map<String, Object> enrichmentLocks = new ConcurrentHashMap<>();

    public PlaceProviderService(
            List<PlaceProvider> providers,
            PlaceRepository placeRepository,
            DestinationResolutionService destinationResolutionService,
            @Value("${journy.places.max-distance-km:20}") double maxDistanceKm,
            @Value("${journy.places.cache-ttl:PT24H}") Duration cacheTtl,
            @Value("${journy.places.cache-minimum-for-you:10}") int cacheMinimumForYou,
            @Value("${journy.places.cache-minimum-category:4}") int cacheMinimumCategory
    ) {
        this.providers = providers;
        this.placeRepository = placeRepository;
        this.destinationResolutionService = destinationResolutionService;
        this.maxDistanceKm = maxDistanceKm;
        this.cacheTtl = cacheTtl;
        this.cacheMinimumForYou = cacheMinimumForYou;
        this.cacheMinimumCategory = cacheMinimumCategory;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int enrichCity(String city, PlaceCategory category, int limit) {
        if (city == null || city.isBlank()) {
            return 0;
        }
        return destinationResolutionService.resolve(city)
                .map(destination -> enrichDestination(destination, category, limit))
                .orElse(0);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int enrichDestination(ResolvedDestination destination, PlaceCategory category, int limit) {
        if (destination == null || limit <= 0) {
            return 0;
        }
        List<Place> cached = loadDestinationPlaces(destination, category, category == null, limit);
        return cached.size();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Place> loadCityPlaces(String city, PlaceCategory category, boolean forYou, int limit) {
        if (city == null || city.isBlank()) {
            return List.of();
        }
        return destinationResolutionService.resolve(city)
                .map(destination -> {
                    log.info("place_destination requested={} resolved={} latitude={} longitude={}", city,
                            destination.locality(), destination.latitude(), destination.longitude());
                    return loadDestinationPlaces(destination, category, forYou, limit);
                })
                .orElseGet(() -> {
                    log.info("place_cache resolution_failure city={} category={}", city.trim(), categoryKey(category, forYou));
                    return List.of();
                });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Place> loadDestinationPlaces(ResolvedDestination destination, PlaceCategory category, boolean forYou, int limit) {
        if (destination == null || limit <= 0) {
            return List.of();
        }
        String lockKey = lockKey(destination, category);
        Object lock = enrichmentLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                int minimum = cacheMinimum(category, forYou, limit);
                Instant cutoff = Instant.now().minus(cacheTtl);
                List<Place> freshCache = cachedPlaces(destination.locality(), category, forYou, cutoff);
                if (freshCache.size() >= minimum) {
                    log.info(
                            "place_cache hit city={} category={} fresh={} minimum={}",
                            destination.locality(),
                            categoryKey(category, forYou),
                            freshCache.size(),
                            minimum
                    );
                    return freshCache.stream().limit(limit).toList();
                }

                List<Place> usableCache = cachedPlaces(destination.locality(), category, forYou, null);
                log.info(
                        "place_cache {} city={} category={} fresh={} usable={} minimum={}",
                        usableCache.isEmpty() ? "miss" : "stale_or_insufficient",
                        destination.locality(),
                        categoryKey(category, forYou),
                        freshCache.size(),
                        usableCache.size(),
                        minimum
                );

                int saved = refreshDestination(destination, category, limit);
                List<Place> refreshed = cachedPlaces(destination.locality(), category, forYou, null);
                if (saved == 0 && !usableCache.isEmpty()) {
                    log.info(
                            "place_cache stale_returned city={} category={} usable={} refreshed=0",
                            destination.locality(),
                            categoryKey(category, forYou),
                            usableCache.size()
                    );
                    return usableCache.stream().limit(limit).toList();
                }
                return refreshed.stream().limit(limit).toList();
            } finally {
                enrichmentLocks.remove(lockKey);
            }
        }
    }

    public List<ExternalPlaceCandidate> discover(ResolvedDestination destination, PlaceCategory category, int limit) {
        if (destination == null || limit <= 0) {
            return List.of();
        }
        java.util.ArrayList<ExternalPlaceCandidate> discovered = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlaceProvider provider : providers) {
            List<ExternalPlaceCandidate> candidates;
            try {
                candidates = provider.search(destination, category, limit);
            } catch (RuntimeException exception) {
                log.warn(
                        "place_provider failure provider={} city={} category={} message={}",
                        provider.name(),
                        destination.locality(),
                        categoryKey(category, category == null),
                        exception.getMessage()
                );
                candidates = List.of();
            }
            for (ExternalPlaceCandidate candidate : candidates) {
                if (!validCandidate(destination, candidate) || !seen.add(identityFor(candidate))) {
                    continue;
                }
                discovered.add(candidate);
            }
            if (discovered.size() >= limit) {
                break;
            }
        }
        return discovered.stream().limit(limit).toList();
    }

    private int refreshDestination(ResolvedDestination destination, PlaceCategory category, int limit) {
        List<ExternalPlaceCandidate> candidates = discover(destination, category, limit);
        log.info(
                "place_provider refresh city={} category={} candidates={}",
                destination.locality(),
                categoryKey(category, category == null),
                candidates.size()
        );
        int saved = 0;
        for (ExternalPlaceCandidate candidate : candidates) {
            upsert(candidate);
            saved++;
        }
        log.info(
                "place_cache refreshed city={} category={} saved={}",
                destination.locality(),
                categoryKey(category, category == null),
                saved
        );
        return saved;
    }

    private List<Place> cachedPlaces(String city, PlaceCategory category, boolean forYou, Instant cutoff) {
        if (cutoff == null) {
            return forYou
                    ? placeRepository.findProviderCachedByCity(city)
                    : placeRepository.findProviderCachedByCityAndCategory(city, category);
        }
        return forYou
                ? placeRepository.findFreshProviderCachedByCity(city, cutoff)
                : placeRepository.findFreshProviderCachedByCityAndCategory(city, category, cutoff);
    }

    private int cacheMinimum(PlaceCategory category, boolean forYou, int limit) {
        int configured = forYou || category == null ? cacheMinimumForYou : cacheMinimumCategory;
        return Math.max(1, Math.min(limit, configured));
    }

    private String categoryKey(PlaceCategory category, boolean forYou) {
        return forYou || category == null ? "for-you" : category.name();
    }

    private boolean validCandidate(ResolvedDestination destination, ExternalPlaceCandidate candidate) {
        if (candidate == null || candidate.name() == null || candidate.name().isBlank()) {
            return false;
        }
        if (!Double.isFinite(candidate.latitude()) || !Double.isFinite(candidate.longitude())) {
            return false;
        }
        if (candidate.latitude() < -90 || candidate.latitude() > 90 || candidate.longitude() < -180 || candidate.longitude() > 180) {
            return false;
        }
        if (candidate.latitude() == 0 || candidate.longitude() == 0) {
            return false;
        }
        return distanceKm(destination.latitude(), destination.longitude(), candidate.latitude(), candidate.longitude()) <= maxDistanceKm;
    }

    private double distanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double earthRadiusKm = 6371.0;
        double latDelta = Math.toRadians(toLatitude - fromLatitude);
        double lonDelta = Math.toRadians(toLongitude - fromLongitude);
        double fromLat = Math.toRadians(fromLatitude);
        double toLat = Math.toRadians(toLatitude);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(fromLat) * Math.cos(toLat)
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private String identityFor(ExternalPlaceCandidate candidate) {
        if (candidate.providerPlaceId() != null && !candidate.providerPlaceId().isBlank()) {
            return candidate.provider() + ":" + candidate.providerPlaceId();
        }
        return candidate.provider()
                + ":"
                + candidate.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                + ":"
                + Math.round(candidate.latitude() * 10000)
                + ":"
                + Math.round(candidate.longitude() * 10000);
    }

    private String lockKey(ResolvedDestination destination, PlaceCategory category) {
        String providerId = destination.providerPlaceId() == null ? destination.locality() : destination.providerPlaceId();
        return providerId.trim().toLowerCase(Locale.ROOT) + ":" + (category == null ? "all" : category.name());
    }

    private void upsert(ExternalPlaceCandidate candidate) {
        Place place = existingPlace(candidate).orElseGet(Place::new);
        place.setId(place.getId() == null ? idFor(candidate) : place.getId());
        place.setProvider(candidate.provider());
        place.setProviderPlaceId(candidate.providerPlaceId());
        place.setName(candidate.name());
        place.setCity(candidate.city());
        place.setCategory(candidate.category());
        place.setDescription(candidate.description());
        place.setPriceLevel(candidate.priceLevel());
        place.setRating(candidate.rating());
        place.setImageUrl(candidate.imageUrl());
        place.setAddress(candidate.address());
        place.setWebsite(candidate.website());
        place.setLatitude(candidate.latitude());
        place.setLongitude(candidate.longitude());
        place.setOpeningHours(candidate.openingHours());
        place.setEstimatedVisitMinutes(candidate.estimatedVisitMinutes());
        place.setTags(candidate.tags());
        place.setProviderFetchedAt(Instant.now());
        placeRepository.save(place);
    }

    private java.util.Optional<Place> existingPlace(ExternalPlaceCandidate candidate) {
        if (candidate.providerPlaceId() != null && !candidate.providerPlaceId().isBlank()) {
            return placeRepository.findByProviderIgnoreCaseAndProviderPlaceId(candidate.provider(), candidate.providerPlaceId());
        }
        return placeRepository.findById(idFor(candidate));
    }

    private String idFor(ExternalPlaceCandidate candidate) {
        String stableIdentity = candidate.providerPlaceId() == null || candidate.providerPlaceId().isBlank()
                ? candidate.name() + "_" + Math.round(candidate.latitude() * 10000) + "_" + Math.round(candidate.longitude() * 10000)
                : candidate.providerPlaceId();
        return candidate.provider() + "_" + stableIdentity.replaceAll("[^a-zA-Z0-9]+", "_");
    }
}
