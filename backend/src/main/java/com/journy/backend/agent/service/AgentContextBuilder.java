package com.journy.backend.agent.service;

import com.journy.backend.agent.dto.AgentContext;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.savedplace.model.SavedPlace;
import com.journy.backend.savedplace.repository.SavedPlaceRepository;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AgentContextBuilder {
    private final SavedPlaceRepository savedPlaceRepository;

    public AgentContextBuilder(SavedPlaceRepository savedPlaceRepository) {
        this.savedPlaceRepository = savedPlaceRepository;
    }

    public AgentContext build(UserAccount user, Trip trip, ItineraryDay day, List<ItineraryDay> itineraryDays) {
        List<SavedPlace> savedPlaces = savedPlaceRepository.findTop8ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail());
        return new AgentContext(
                userContext(user, trip, savedPlaces),
                tripContext(trip),
                dayContext(day),
                itineraryDays.stream().map(this::dayContext).toList()
        );
    }

    private AgentContext.UserAgentContext userContext(UserAccount user, Trip trip, List<SavedPlace> savedPlaces) {
        return new AgentContext.UserAgentContext(
                user.getId(),
                user.getTravelStyle(),
                fallback(user.getDefaultPace(), "BALANCED"),
                fallback(user.getDefaultBudget(), "BALANCED"),
                fallback(user.getFoodDiscovery(), "LOCAL_FIRST"),
                tasteSignals(trip, savedPlaces),
                savedCategorySignals(savedPlaces),
                savedPlaces.stream().map(this::savedPlaceSignal).toList(),
                planningStrategy(trip)
        );
    }

    private AgentContext.TripAgentContext tripContext(Trip trip) {
        return new AgentContext.TripAgentContext(
                trip.getId(),
                trip.getDestination(),
                trip.getBudget().name(),
                trip.getPace().name(),
                trip.getInterests().stream().map(Enum::name).toList(),
                trip.getStartingArea()
        );
    }

    private AgentContext.DayAgentContext dayContext(ItineraryDay day) {
        return new AgentContext.DayAgentContext(
                day.getDayNumber(),
                day.getTitle(),
                day.getSummary(),
                day.getWalkKm(),
                day.getStops().size(),
                nextStop(day),
                day.getStops().stream()
                        .filter(ItineraryStop::isOptionalStop)
                        .map(ItineraryStop::getTitle)
                        .toList(),
                day.getStops().stream().map(this::stopContext).toList()
        );
    }

    private AgentContext.StopAgentContext stopContext(ItineraryStop stop) {
        return new AgentContext.StopAgentContext(
                stop.getStopOrder(),
                stop.getTitle(),
                stop.getCategory(),
                stop.getTimeWindow(),
                stop.getNote(),
                stop.isOptionalStop(),
                stop.getLatitude(),
                stop.getLongitude()
        );
    }

    private AgentContext.SavedPlaceSignal savedPlaceSignal(SavedPlace place) {
        return new AgentContext.SavedPlaceSignal(
                place.getName(),
                place.getCity(),
                place.getCategory(),
                place.getPriceLevel(),
                place.getRating(),
                place.getTags()
        );
    }

    private List<String> tasteSignals(Trip trip, List<SavedPlace> savedPlaces) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        trip.getInterests().forEach(interest -> addSignal(weights, signalKey(interest), 3));
        addSignal(weights, trip.getPace().name().equals("RELAXED") ? "Easy walking" : "Route rhythm", 2);
        if (trip.getBudget().name().equals("LEAN")) {
            addSignal(weights, "Low-cost picks", 2);
        }
        savedPlaces.forEach(place -> addSignal(weights, signalKey(place.getCategory()), 2));

        if (weights.isEmpty()) {
            addSignal(weights, "Balanced route", 1);
        }

        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> savedCategorySignals(List<SavedPlace> savedPlaces) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        savedPlaces.forEach(place -> addSignal(weights, signalKey(place.getCategory()), 1));
        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .toList();
    }

    private AgentContext.PlanningStrategyContext planningStrategy(Trip trip) {
        List<String> signals = new ArrayList<>();
        signals.add(label(trip.getPace().name()) + " pace");
        signals.add(label(trip.getBudget().name()) + " budget");
        trip.getInterests().stream().limit(3).map(this::interestLabel).forEach(signals::add);
        String title = label(trip.getPace().name()) + " " + routeFocus(trip) + " plan";
        String description = "Plan around " + trip.dayCount() + " days in " + trip.getDestination()
                + " with " + label(trip.getBudget().name()).toLowerCase(Locale.ROOT)
                + " spend and " + label(trip.getPace().name()).toLowerCase(Locale.ROOT) + " rhythm.";
        return new AgentContext.PlanningStrategyContext(title, description, signals);
    }

    private String nextStop(ItineraryDay day) {
        return day.getStops().stream()
                .filter(stop -> !stop.isOptionalStop())
                .findFirst()
                .or(() -> day.getStops().stream().findFirst())
                .map(ItineraryStop::getTitle)
                .orElse(null);
    }

    private void addSignal(Map<String, Integer> weights, String key, int weight) {
        weights.merge(key, weight, Integer::sum);
    }

    private String signalKey(TravelInterest interest) {
        return switch (interest) {
            case LOCAL_FOOD -> "Local food";
            case MUSEUMS, CULTURE -> "Culture";
            case COFFEE -> "Coffee breaks";
            case WALKING -> "Easy walking";
            case SHOPPING -> "Shopping";
            case NIGHTLIFE -> "Nightlife";
            case FREE_ACTIVITIES -> "Low-cost picks";
        };
    }

    private String signalKey(String category) {
        String normalized = category == null ? "" : category.toUpperCase(Locale.ROOT);
        if (normalized.contains("FOOD")) return "Local food";
        if (normalized.contains("COFFEE")) return "Coffee breaks";
        if (normalized.contains("CULTURE")) return "Culture";
        if (normalized.contains("FREE")) return "Low-cost picks";
        return "Easy walking";
    }

    private String routeFocus(Trip trip) {
        if (trip.getInterests().contains(TravelInterest.LOCAL_FOOD)) return "food-led";
        if (trip.getInterests().contains(TravelInterest.COFFEE)) return "coffee-aware";
        if (trip.getInterests().contains(TravelInterest.MUSEUMS) || trip.getInterests().contains(TravelInterest.CULTURE)) return "culture-led";
        if (trip.getInterests().contains(TravelInterest.WALKING)) return "walkable";
        return "balanced";
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
