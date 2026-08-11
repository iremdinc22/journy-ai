package com.journy.backend.agent.dto;

import java.util.List;

public record AgentContext(
        UserAgentContext userProfile,
        TripAgentContext trip,
        DayAgentContext day,
        List<DayAgentContext> itineraryDays
) {
    public record UserAgentContext(
            String userId,
            String travelStyle,
            String defaultPace,
            String defaultBudget,
            String foodDiscovery,
            List<String> tasteSignals,
            List<String> savedCategorySignals,
            List<SavedPlaceSignal> savedPlaces,
            PlanningStrategyContext planningStrategy
    ) {
    }

    public record SavedPlaceSignal(
            String name,
            String city,
            String category,
            String priceLevel,
            double rating,
            String tags
    ) {
    }

    public record PlanningStrategyContext(
            String title,
            String description,
            List<String> signals
    ) {
    }

    public record TripAgentContext(
            String tripId,
            String destination,
            String budget,
            String pace,
            List<String> interests,
            String startingArea
    ) {
    }

    public record DayAgentContext(
            int dayNumber,
            String title,
            String summary,
            double walkKm,
            int stopCount,
            String nextStop,
            List<String> optionalStops,
            List<StopAgentContext> stops
    ) {
    }

    public record StopAgentContext(
            int order,
            String title,
            String category,
            String timeWindow,
            String note,
            boolean optional,
            Double latitude,
            Double longitude
    ) {
    }
}
