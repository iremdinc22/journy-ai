package com.journy.backend.profile.mapper;

import com.journy.backend.profile.dto.ProfileResponse;
import com.journy.backend.savedplace.model.SavedPlace;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProfileMapper {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    public ProfileResponse toResponse(UserAccount user, Trip currentTrip, List<Trip> savedTrips, List<SavedPlace> savedPlaces, long favoriteCount) {
        return new ProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getTravelStyle(),
                currentTrip == null ? null : toCurrentTrip(currentTrip),
                new ProfileResponse.Preferences(
                        fallback(user.getDefaultPace(), "BALANCED"),
                        fallback(user.getDefaultBudget(), "BALANCED"),
                        fallback(user.getFoodDiscovery(), "LOCAL_FIRST"),
                        user.isPlanChangeNotifications(),
                        user.isFoodWindowNotifications()
                ),
                tasteSignals(currentTrip, savedPlaces),
                favoriteCount,
                savedTrips.stream().map(this::toSavedPlan).toList(),
                savedPlaces.stream().map(this::toSavedPlace).toList()
        );
    }

    private ProfileResponse.CurrentTrip toCurrentTrip(Trip trip) {
        return new ProfileResponse.CurrentTrip(
                trip.getId(),
                trip.getDestination(),
                trip.getStartingArea(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                DATE_FORMATTER.format(trip.getStartDate()) + " - " + DATE_FORMATTER.format(trip.getEndDate()),
                trip.getTravelerType().name(),
                trip.getBudget().name(),
                trip.getPace().name(),
                trip.getInterests().stream().map(Enum::name).toList(),
                trip.getTotalStops(),
                trip.getFoodPicks(),
                trip.getAverageWalkKm(),
                planningStrategy(trip)
        );
    }

    private ProfileResponse.SavedPlan toSavedPlan(Trip trip) {
        return new ProfileResponse.SavedPlan(
                trip.getId(),
                trip.getDestination(),
                trip.dayCount() + " days · " + trip.getPace().name(),
                trip.getTotalStops(),
                trip.getFoodPicks(),
                trip.getAverageWalkKm()
        );
    }

    private ProfileResponse.SavedPlace toSavedPlace(SavedPlace place) {
        return new ProfileResponse.SavedPlace(
                place.getPlaceId(),
                place.getName(),
                place.getCity(),
                place.getCategory(),
                place.getImageUrl(),
                place.getRating()
        );
    }

    private List<ProfileResponse.TasteSignal> tasteSignals(Trip currentTrip, List<SavedPlace> savedPlaces) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (currentTrip != null) {
            currentTrip.getInterests().forEach(interest -> addSignal(weights, signalKey(interest), 3));
            addSignal(weights, currentTrip.getPace().name().equals("RELAXED") ? "EASY_WALKING" : "ROUTE_RHYTHM", 2);
            if (currentTrip.getBudget().name().equals("LEAN")) {
                addSignal(weights, "FREE_ACTIVITIES", 2);
            }
        }
        savedPlaces.forEach(place -> addSignal(weights, signalKey(place.getCategory()), 2));

        if (weights.isEmpty()) {
            addSignal(weights, "ROUTE_RHYTHM", 1);
        }

        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(entry -> toTasteSignal(entry.getKey(), entry.getValue(), savedPlaces))
                .toList();
    }

    private ProfileResponse.PlanningStrategy planningStrategy(Trip trip) {
        List<String> signals = new ArrayList<>();
        signals.add(label(trip.getPace().name()) + " pace");
        signals.add(label(trip.getBudget().name()) + " budget");
        trip.getInterests().stream().limit(3).map(this::interestLabel).forEach(signals::add);
        String title = label(trip.getPace().name()) + " " + routeFocus(trip) + " plan";
        String description = "Journy is shaping " + trip.getDestination() + " around " + trip.dayCount()
                + " days, " + label(trip.getBudget().name()).toLowerCase() + " spend and "
                + label(trip.getPace().name()).toLowerCase() + " daily rhythm.";
        return new ProfileResponse.PlanningStrategy(title, description, signals);
    }

    private String routeFocus(Trip trip) {
        if (trip.getInterests().contains(TravelInterest.LOCAL_FOOD)) return "food-led";
        if (trip.getInterests().contains(TravelInterest.COFFEE)) return "coffee-aware";
        if (trip.getInterests().contains(TravelInterest.MUSEUMS) || trip.getInterests().contains(TravelInterest.CULTURE)) return "culture-led";
        if (trip.getInterests().contains(TravelInterest.WALKING)) return "walkable";
        return "balanced";
    }

    private void addSignal(Map<String, Integer> weights, String key, int weight) {
        weights.merge(key, weight, Integer::sum);
    }

    private String signalKey(TravelInterest interest) {
        return switch (interest) {
            case LOCAL_FOOD -> "LOCAL_FOOD";
            case MUSEUMS, CULTURE -> "CULTURE";
            case COFFEE -> "COFFEE";
            case WALKING -> "EASY_WALKING";
            case SHOPPING -> "SHOPPING";
            case NIGHTLIFE -> "NIGHTLIFE";
            case FREE_ACTIVITIES -> "FREE_ACTIVITIES";
        };
    }

    private String signalKey(String category) {
        String normalized = category == null ? "" : category.toUpperCase(Locale.ROOT);
        if (normalized.contains("FOOD")) return "LOCAL_FOOD";
        if (normalized.contains("COFFEE")) return "COFFEE";
        if (normalized.contains("CULTURE")) return "CULTURE";
        if (normalized.contains("FREE")) return "FREE_ACTIVITIES";
        return "EASY_WALKING";
    }

    private ProfileResponse.TasteSignal toTasteSignal(String key, int weight, List<SavedPlace> savedPlaces) {
        long matchingSaved = savedPlaces.stream().filter(place -> signalKey(place.getCategory()).equals(key)).count();
        String detail = matchingSaved > 0
                ? matchingSaved + " saved " + (matchingSaved == 1 ? "place" : "places")
                : weight >= 3 ? "From TripSetup choices" : "Learned from your route";
        return switch (key) {
            case "LOCAL_FOOD" -> new ProfileResponse.TasteSignal("Local food", detail, "restaurant");
            case "CULTURE" -> new ProfileResponse.TasteSignal("Culture", detail, "museum");
            case "COFFEE" -> new ProfileResponse.TasteSignal("Coffee breaks", detail, "coffee");
            case "FREE_ACTIVITIES" -> new ProfileResponse.TasteSignal("Low-cost picks", detail, "leaf");
            case "SHOPPING" -> new ProfileResponse.TasteSignal("Shopping", detail, "bag");
            case "NIGHTLIFE" -> new ProfileResponse.TasteSignal("Nightlife", detail, "moon");
            case "ROUTE_RHYTHM" -> new ProfileResponse.TasteSignal("Route rhythm", detail, "map");
            default -> new ProfileResponse.TasteSignal("Easy walking", detail, "walk");
        };
    }

    private String interestLabel(TravelInterest interest) {
        return switch (interest) {
            case LOCAL_FOOD -> "Local food";
            case FREE_ACTIVITIES -> "Free activities";
            default -> label(interest.name());
        };
    }

    private String label(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (normalized.isBlank()) return "";
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
