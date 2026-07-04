package com.journy.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiItinerarySuggestionRequest(
        @NotBlank String tripId,
        @NotNull Integer dayNumber,
        @NotBlank String action
) {
}
