package com.journy.backend.destination.provider;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DestinationCoordinateResolver {
    private final List<DestinationProvider> destinationProviders;
    private final Map<String, DestinationCoordinates> cache = new ConcurrentHashMap<>();

    public DestinationCoordinateResolver(List<DestinationProvider> destinationProviders) {
        this.destinationProviders = destinationProviders;
    }

    public DestinationCoordinates coordinatesFor(String city) {
        String normalized = city == null ? "" : city.trim();
        if (normalized.isBlank()) {
            return new DestinationCoordinates(52.3676, 4.9041);
        }
        return cache.computeIfAbsent(normalized.toLowerCase(Locale.ROOT), ignored -> resolve(normalized));
    }

    public double latitudeFor(String city, int index) {
        return coordinatesFor(city).latitude() + offset(index);
    }

    public double longitudeFor(String city, int index) {
        return coordinatesFor(city).longitude() + offset(index);
    }

    private DestinationCoordinates resolve(String city) {
        DestinationCoordinates known = knownCoordinates(city);
        if (known != null) {
            return known;
        }
        return destinationProviders.stream()
                .map(provider -> provider.resolve(city))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst()
                .map(candidate -> new DestinationCoordinates(candidate.latitude(), candidate.longitude()))
                .orElseGet(() -> new DestinationCoordinates(52.3676, 4.9041));
    }

    private double offset(int index) {
        return Math.max(1, index) * 0.002;
    }

    private DestinationCoordinates knownCoordinates(String city) {
        return switch (normalize(city)) {
            case "amsterdam" -> new DestinationCoordinates(52.3676, 4.9041);
            case "paris" -> new DestinationCoordinates(48.8566, 2.3522);
            case "rome" -> new DestinationCoordinates(41.9028, 12.4964);
            case "barcelona" -> new DestinationCoordinates(41.3874, 2.1686);
            case "tokyo" -> new DestinationCoordinates(35.6762, 139.6503);
            case "london" -> new DestinationCoordinates(51.5072, -0.1276);
            case "lisbon" -> new DestinationCoordinates(38.7223, -9.1393);
            case "prague" -> new DestinationCoordinates(50.0755, 14.4378);
            case "vienna" -> new DestinationCoordinates(48.2082, 16.3738);
            case "berlin" -> new DestinationCoordinates(52.5200, 13.4050);
            case "copenhagen" -> new DestinationCoordinates(55.6761, 12.5683);
            case "istanbul" -> new DestinationCoordinates(41.0082, 28.9784);
            case "new york" -> new DestinationCoordinates(40.7128, -74.0060);
            case "kyoto" -> new DestinationCoordinates(35.0116, 135.7681);
            case "madrid" -> new DestinationCoordinates(40.4168, -3.7038);
            case "milan", "milano" -> new DestinationCoordinates(45.4642, 9.1900);
            case "canakkale" -> new DestinationCoordinates(40.1553, 26.4142);
            case "edirne" -> new DestinationCoordinates(41.6771, 26.5557);
            case "bursa" -> new DestinationCoordinates(40.1828, 29.0663);
            case "eskişehir", "eskisehir" -> new DestinationCoordinates(39.7667, 30.5256);
            case "ankara" -> new DestinationCoordinates(39.9334, 32.8597);
            case "izmir", "i̇zmir" -> new DestinationCoordinates(38.4237, 27.1428);
            case "antalya" -> new DestinationCoordinates(36.8969, 30.7133);
            case "edinburgh", "edinburg" -> new DestinationCoordinates(55.9533, -3.1883);
            default -> null;
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
