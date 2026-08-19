package com.journy.backend.feedback.dto;

import com.journy.backend.feedback.model.TasteFeedbackAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TasteFeedbackRequest(
        String placeId,
        @NotBlank String placeName,
        @NotBlank String category,
        @NotNull TasteFeedbackAction action,
        String reason
) {
}
