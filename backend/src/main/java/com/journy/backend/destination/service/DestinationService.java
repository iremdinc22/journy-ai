package com.journy.backend.destination.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.destination.dto.DestinationResponse;
import com.journy.backend.destination.mapper.DestinationMapper;
import com.journy.backend.destination.provider.DestinationCandidate;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationCoordinates;
import com.journy.backend.destination.provider.DestinationImageResolver;
import com.journy.backend.destination.provider.DestinationProvider;
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
    private final List<DestinationProvider> destinationProviders;
    private final DestinationImageResolver destinationImageResolver;
    private final DestinationCoordinateResolver destinationCoordinateResolver;

    public DestinationService(
            DestinationRepository destinationRepository,
            DestinationMapper destinationMapper,
            PlaceRepository placeRepository,
            List<DestinationProvider> destinationProviders,
            DestinationImageResolver destinationImageResolver,
            DestinationCoordinateResolver destinationCoordinateResolver
    ) {
        this.destinationRepository = destinationRepository;
        this.destinationMapper = destinationMapper;
        this.placeRepository = placeRepository;
        this.destinationProviders = destinationProviders;
        this.destinationImageResolver = destinationImageResolver;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
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
            matches.add(dynamicDestination(query.trim()));
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

    private DestinationResponse dynamicDestination(String city) {
        DestinationCandidate candidate = resolve(city);
        String resolvedCity = candidate == null ? city : candidate.name();
        String country = candidate == null ? countryFor(city) : candidate.country();
        DestinationCoordinates coordinates = destinationCoordinateResolver.coordinatesFor(resolvedCity);
        int placeCount = (int) placeRepository.countByCityIgnoreCase(resolvedCity);
        return new DestinationResponse(
                "dynamic-" + slug(resolvedCity),
                resolvedCity,
                country,
                dynamicDescription(resolvedCity, country, candidate),
                destinationImageResolver.imageFor(resolvedCity, country),
                tagsFor(resolvedCity),
                "Provider-backed city planning",
                Math.max(placeCount, 0),
                5.6,
                true,
                false,
                candidate == null ? coordinates.latitude() : candidate.latitude(),
                candidate == null ? coordinates.longitude() : candidate.longitude(),
                candidate == null ? "dynamic" : candidate.provider(),
                candidate == null ? null : candidate.providerPlaceId()
        );
    }

    private DestinationCandidate resolve(String query) {
        return destinationProviders.stream()
                .map(provider -> provider.resolve(query))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst()
                .orElse(null);
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String dynamicDescription(String city, String country, DestinationCandidate candidate) {
        if (candidate != null) {
            return "Journy found " + city + " in " + country + " and can search live place providers to build a route there.";
        }
        return "Journy can search live place providers and build a starter route for " + city + ".";
    }

    private String countryFor(String city) {
        return switch (normalize(city)) {
            case "milan", "milano" -> "Italy";
            case "munich", "münchen" -> "Germany";
            case "brussels", "bruxelles", "brussel" -> "Belgium";
            case "budapest" -> "Hungary";
            case "zurich", "zürich" -> "Switzerland";
            case "stockholm" -> "Sweden";
            case "oslo" -> "Norway";
            case "athens", "atina" -> "Greece";
            case "dublin" -> "Ireland";
            case "canakkale", "edirne" -> "Turkey";
            case "bursa", "eskisehir", "ankara", "izmir", "antalya" -> "Turkey";
            case "edinburgh", "edinburg" -> "United Kingdom";
            default -> "Provider-backed";
        };
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
