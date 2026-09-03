package com.journy.backend.destination.service;

import com.journy.backend.destination.provider.DestinationCandidate;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.destination.provider.DestinationCoordinates;
import com.journy.backend.destination.provider.DestinationProvider;
import com.journy.backend.destination.provider.ResolvedDestination;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DestinationResolutionService {
    private final List<DestinationProvider> destinationProviders;
    private final DestinationCoordinateResolver destinationCoordinateResolver;

    public DestinationResolutionService(
            List<DestinationProvider> destinationProviders,
            DestinationCoordinateResolver destinationCoordinateResolver
    ) {
        this.destinationProviders = destinationProviders;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
    }

    public Optional<ResolvedDestination> resolve(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return Optional.empty();
        }

        Optional<ResolvedDestination> known = knownDestination(normalizedQuery);
        if (known.isPresent()) {
            return known;
        }

        return destinationProviders.stream()
                .map(provider -> provider.resolve(normalizedQuery))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::hasUsableCoordinates)
                .findFirst()
                .map(candidate -> fromCandidate(normalizedQuery, candidate));
    }

    private Optional<ResolvedDestination> knownDestination(String query) {
        return destinationCoordinateResolver.knownCoordinatesFor(query)
                .map(coordinates -> new ResolvedDestination(
                        query,
                        canonicalLocality(query) + ", " + countryFor(query),
                        canonicalLocality(query),
                        null,
                        countryFor(query),
                        coordinates.latitude(),
                        coordinates.longitude(),
                        "known",
                        normalize(query)
                ));
    }

    private ResolvedDestination fromCandidate(String originalQuery, DestinationCandidate candidate) {
        return new ResolvedDestination(
                originalQuery,
                fallback(candidate.displayName(), candidate.name()),
                fallback(candidate.name(), originalQuery),
                null,
                fallback(candidate.country(), null),
                candidate.latitude(),
                candidate.longitude(),
                candidate.provider(),
                candidate.providerPlaceId()
        );
    }

    private boolean hasUsableCoordinates(DestinationCandidate candidate) {
        return Double.isFinite(candidate.latitude())
                && Double.isFinite(candidate.longitude())
                && candidate.latitude() >= -90
                && candidate.latitude() <= 90
                && candidate.longitude() >= -180
                && candidate.longitude() <= 180
                && !(candidate.latitude() == 0 && candidate.longitude() == 0);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String canonicalLocality(String city) {
        return switch (normalize(city)) {
            case "amsterdam" -> "Amsterdam";
            case "paris" -> "Paris";
            case "rome", "roma" -> "Rome";
            case "barcelona" -> "Barcelona";
            case "tokyo" -> "Tokyo";
            case "london" -> "London";
            case "lisbon" -> "Lisbon";
            case "prague" -> "Prague";
            case "vienna" -> "Vienna";
            case "berlin" -> "Berlin";
            case "copenhagen" -> "Copenhagen";
            case "istanbul" -> "Istanbul";
            case "new york" -> "New York";
            case "kyoto" -> "Kyoto";
            case "madrid" -> "Madrid";
            case "milan", "milano" -> "Milan";
            case "canakkale" -> "Canakkale";
            case "edirne" -> "Edirne";
            case "bursa" -> "Bursa";
            case "eskisehir" -> "Eskisehir";
            case "ankara" -> "Ankara";
            case "izmir" -> "Izmir";
            case "antalya" -> "Antalya";
            case "edinburgh", "edinburg" -> "Edinburgh";
            default -> city.trim();
        };
    }

    private String countryFor(String city) {
        return switch (normalize(city)) {
            case "amsterdam" -> "Netherlands";
            case "paris" -> "France";
            case "rome", "roma", "milan", "milano" -> "Italy";
            case "barcelona", "madrid" -> "Spain";
            case "tokyo", "kyoto" -> "Japan";
            case "london", "edinburgh", "edinburg" -> "United Kingdom";
            case "lisbon" -> "Portugal";
            case "prague" -> "Czechia";
            case "vienna" -> "Austria";
            case "berlin" -> "Germany";
            case "copenhagen" -> "Denmark";
            case "istanbul", "canakkale", "edirne", "bursa", "eskisehir", "ankara", "izmir", "antalya" -> "Turkey";
            case "new york" -> "United States";
            default -> "Provider-backed";
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
