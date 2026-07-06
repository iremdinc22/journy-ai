package com.journy.backend.profile.dto;

import java.util.List;

public record ProfileResponse(
        String id,
        String fullName,
        String travelStyle,
        CurrentTrip currentTrip,
        List<TasteSignal> tasteProfile,
        List<SavedPlan> savedPlans,
        List<SavedPlace> savedPlaces
) {
    public record CurrentTrip(
            String destination,
            String dates,
            int stops,
            int foodPicks,
            double averageWalkKm
    ) {
    }

    public record TasteSignal(
            String title,
            String description,
            String icon
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
