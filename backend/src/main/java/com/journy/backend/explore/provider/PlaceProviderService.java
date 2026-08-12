package com.journy.backend.explore.provider;

import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaceProviderService {
    private final List<PlaceProvider> providers;
    private final PlaceRepository placeRepository;
    private final Map<String, Object> enrichmentLocks = new ConcurrentHashMap<>();

    public PlaceProviderService(List<PlaceProvider> providers, PlaceRepository placeRepository) {
        this.providers = providers;
        this.placeRepository = placeRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int enrichCity(String city, PlaceCategory category, int limit) {
        if (city == null || city.isBlank()) {
            return 0;
        }
        String lockKey = city.trim().toLowerCase() + ":" + (category == null ? "all" : category.name());
        Object lock = enrichmentLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                return enrichCityLocked(city, category, limit);
            } finally {
                enrichmentLocks.remove(lockKey);
            }
        }
    }

    private int enrichCityLocked(String city, PlaceCategory category, int limit) {
        int saved = 0;
        Set<String> seen = new HashSet<>();
        for (PlaceProvider provider : providers) {
            List<ExternalPlaceCandidate> candidates = provider.search(city.trim(), category, limit);
            for (ExternalPlaceCandidate candidate : candidates) {
                if (!seen.add(candidate.provider() + ":" + candidate.providerPlaceId())) {
                    continue;
                }
                upsert(candidate);
                saved++;
            }
            if (saved >= limit) {
                break;
            }
        }
        return saved;
    }

    private void upsert(ExternalPlaceCandidate candidate) {
        Place place = placeRepository.findByProviderIgnoreCaseAndProviderPlaceId(candidate.provider(), candidate.providerPlaceId())
                .orElseGet(Place::new);
        place.setId(place.getId() == null ? candidate.provider() + "_" + candidate.providerPlaceId().replaceAll("[^a-zA-Z0-9]+", "_") : place.getId());
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
        placeRepository.save(place);
    }
}
