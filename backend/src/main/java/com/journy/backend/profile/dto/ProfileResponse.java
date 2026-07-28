package com.journy.backend.profile.dto;

import java.util.List;

public record ProfileResponse(
        String id,
        String fullName,
        String travelStyle,
        CurrentTrip currentTrip,
        Preferences preferences,
        List<TasteSignal> tasteProfile,
        long favoriteCount,
        List<SavedPlan> savedPlans,
        List<SavedPlace> savedPlaces
) {
    public record CurrentTrip(
            String id,
            String destination,
            String startingArea,
            String startDate,
            String endDate,
            String dates,
            String travelerType,
            String budget,
            String pace,
            List<String> interests,
            int stops,
            int foodPicks,
            double averageWalkKm,
            PlanningStrategy planningStrategy
    ) {
    }

    public record PlanningStrategy(
            String title,
            String description,
            List<String> signals
    ) {
    }

    public record TasteSignal(
            String title,
            String description,
            String icon
    ) {
    }

    public record Preferences(
            String defaultPace,
            String defaultBudget,
            String foodDiscovery,
            boolean planChangeNotifications,
            boolean foodWindowNotifications
    ) {
    }

    public record SavedPlan(
            String id,
            String destination,
            String summary,
            int stops,
            int foodPicks,
            double averageWalkKm
    ) {
    }

    public record SavedPlace(
            String placeId,
            String name,
            String city,
            String category,
            String imageUrl,
            double rating
    ) {
    }
}
