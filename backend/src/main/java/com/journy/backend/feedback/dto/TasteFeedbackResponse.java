package com.journy.backend.feedback.dto;

public record TasteFeedbackResponse(
        String id,
        String placeId,
        String placeName,
        String category,
        String action,
        int weight,
        String reason
) {
}
