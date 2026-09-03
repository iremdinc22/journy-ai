package com.journy.backend.itinerary.service;

import com.journy.backend.explore.model.Place;
import com.journy.backend.itinerary.model.ItineraryDay;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validation for newly generated stops only; historical rows are not revalidated. */
public final class PlannerPlaceContract {
    private PlannerPlaceContract() {}

    public static boolean verified(Place place) {
        if (place == null || blank(place.getId()) || blank(place.getName()) || blank(place.getCity())
                || blank(place.getProvider()) || place.getProviderFetchedAt() == null || place.getCategory() == null) {
            return false;
        }
        String provider = place.getProvider().toLowerCase(Locale.ROOT);
        return provider.matches("[a-z0-9_-]+") && !List.of("seed", "starter", "planned_fallback").contains(provider)
                && coordinate(place.getLatitude(), 90) && coordinate(place.getLongitude(), 180)
                && !(place.getLatitude() == 0 && place.getLongitude() == 0);
    }

    public static boolean inDestination(Place place, String destination) {
        return place != null && place.getCity() != null && destination != null
                && place.getCity().trim().equalsIgnoreCase(destination.trim());
    }

    public static Map<String, Identity> snapshot(List<Place> candidates, String destination) {
        return candidates.stream().filter(PlannerPlaceContract::verified)
                .filter(place -> inDestination(place, destination)).map(Identity::of)
                .collect(Collectors.toUnmodifiableMap(Identity::id, Function.identity()));
    }

    public static void validate(List<ItineraryDay> days, Map<String, Identity> candidates) {
        var usedIds = new HashSet<String>();
        for (var day : days) {
            for (var stop : day.getStops()) {
                if (stop.getPlaceId() != null && !usedIds.add(stop.getPlaceId())) {
                    throw new IllegalStateException("Duplicate itinerary Place ID: " + stop.getPlaceId());
                }
                if (stop.getSource() == null || !stop.getSource().startsWith("provider:")) continue;
                Identity identity = stop.getPlaceId() == null ? null : candidates.get(stop.getPlaceId());
                if (identity == null || !Objects.equals(identity.name(), stop.getTitle())
                        || !Objects.equals(identity.source(), stop.getSource())
                        || !Objects.equals(identity.category(), stop.getCategory())
                        || Double.compare(identity.latitude(), stop.getLatitude()) != 0
                        || Double.compare(identity.longitude(), stop.getLongitude()) != 0) {
                    throw new IllegalStateException("Verified stop differs from accepted candidate: " + stop.getPlaceId());
                }
            }
        }
    }

    private static boolean coordinate(Double value, int bound) {
        return value != null && Double.isFinite(value) && value >= -bound && value <= bound;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record Identity(String id, String name, String source, String category, double latitude, double longitude) {
        private static Identity of(Place place) {
            return new Identity(place.getId(), place.getName(), "provider:" + place.getProvider(),
                    place.getCategory().name(), place.getLatitude(), place.getLongitude());
        }
    }
}
