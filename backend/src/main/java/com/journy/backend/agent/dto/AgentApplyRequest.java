package com.journy.backend.agent.dto;

import com.journy.backend.agent.enums.AgentIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentApplyRequest(
        @NotBlank String tripId,
        @NotNull Integer dayNumber,
        @NotNull AgentIntent intent
) {
}
