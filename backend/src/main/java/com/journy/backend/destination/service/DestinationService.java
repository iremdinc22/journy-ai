package com.journy.backend.destination.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.destination.dto.DestinationResponse;
import com.journy.backend.destination.mapper.DestinationMapper;
import com.journy.backend.destination.provider.DestinationImageResolver;
import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.repository.DestinationRepository;
import com.journy.backend.explore.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DestinationService {
    private final DestinationRepository destinationRepository;
    private final DestinationMapper destinationMapper;
    private final PlaceRepository placeRepository;
    private final DestinationImageResolver destinationImageResolver;
    private final DestinationResolutionService destinationResolutionService;

    public DestinationService(
            DestinationRepository destinationRepository,
            DestinationMapper destinationMapper,
            PlaceRepository placeRepository,
            DestinationImageResolver destinationImageResolver,
            DestinationResolutionService destinationResolutionService
    ) {
        this.destinationRepository = destinationRepository;
        this.destinationMapper = destinationMapper;
        this.placeRepository = placeRepository;
        this.destinationImageResolver = destinationImageResolver;
        this.destinationResolutionService = destinationResolutionService;
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return destinationRepository.findTop12ByOrderByPopularDescAvailableDescNameAsc().stream()
                    .map(destinationMapper::toResponse)
                    .toList();
        }

        List<DestinationResponse> matches = new ArrayList<>(destinationRepository
                .findTop12ByNameContainingIgnoreCaseOrCountryContainingIgnoreCaseOrderByAvailableDescNameAsc(query.trim(), query.trim())
                .stream()
                .map(destinationMapper::toResponse)
                .toList());
        if (query.trim().length() > 1 && matches.stream().noneMatch(destination -> destination.name().equalsIgnoreCase(query.trim()))) {
            destinationResolutionService.resolve(query.trim())
                    .map(this::dynamicDestination)
                    .ifPresent(matches::add);
        }
        return matches;
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> popular() {
        return destinationRepository.findTop8ByPopularTrueAndAvailableTrueOrderByNameAsc().stream()
                .map(destinationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DestinationResponse detail(String id) {
        return destinationRepository.findById(id)
                .map(destinationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Destination was not found"));
    }

    private DestinationResponse dynamicDestination(ResolvedDestination resolvedDestination) {
        String resolvedCity = resolvedDestination.locality();
        String country = resolvedDestination.country();
        int placeCount = (int) placeRepository.countByCityIgnoreCase(resolvedCity);
        return new DestinationResponse(
                "dynamic-" + slug(resolvedCity),
                resolvedCity,
                country,
                dynamicDescription(resolvedCity, country),
                destinationImageResolver.imageFor(resolvedCity, country),
                tagsFor(resolvedCity),
                "Provider-backed city planning",
                Math.max(placeCount, 0),
                5.6,
                true,
                false,
                resolvedDestination.latitude(),
                resolvedDestination.longitude(),
                resolvedDestination.provider(),
                resolvedDestination.providerPlaceId(),
                resolvedDestination.originalQuery()
        );
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String dynamicDescription(String city, String country) {
        return "Journy found " + city + " in " + country + " and can search live place providers to build a route there.";
    }

    private String tagsFor(String city) {
        return switch (normalize(city)) {
            case "milan", "milano" -> "Design - aperitivo - cathedral walks";
            case "munich", "münchen" -> "Museums - gardens - beer halls";
            case "brussels", "bruxelles", "brussel" -> "Chocolate - galleries - grand squares";
            case "budapest" -> "Thermal baths - river walks - cafes";
            case "zurich", "zürich" -> "Lake walks - design - old town";
            case "stockholm" -> "Islands - design - coffee";
            case "oslo" -> "Fjord walks - museums - coffee";
            case "athens", "atina" -> "Ancient sites - food - neighborhoods";
            case "dublin" -> "Pubs - literature - walkable streets";
            case "canakkale" -> "Waterfront - history - local food";
            case "edirne" -> "Ottoman heritage - river walks - local food";
            case "bursa" -> "Ottoman heritage - bazaars - mountain views";
            case "eskisehir" -> "Porsuk river - old town - cafes";
            case "ankara" -> "Museums - republic history - cafes";
            case "izmir" -> "Seaside walks - markets - local food";
            case "antalya" -> "Old town - beaches - historic walks";
            case "edinburgh", "edinburg" -> "Castle views - old town - pubs";
            default -> "Provider search - local picks - flexible planning";
        };
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("ç", "c")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ş", "s")
                .replace("ü", "u");
    }
}
