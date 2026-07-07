package com.journy.backend.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentMessageRequest(
        String tripId,
        Integer dayNumber,
        @NotBlank String message
) {
}
