package com.journy.backend.ai.dto;

import java.util.List;

public record AiItinerarySuggestionResponse(
        String title,
        String message,
        String suggestedAction,
        Integer minutesSaved,
        List<String> stopsAffected,
        String routeSummary
) {
}
