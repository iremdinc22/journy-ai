package com.journy.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        String tripId,
        @NotBlank String message
) {
}
