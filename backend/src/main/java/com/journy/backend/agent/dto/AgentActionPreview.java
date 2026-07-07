package com.journy.backend.agent.dto;

import com.journy.backend.agent.enums.AgentIntent;

import java.util.List;

public record AgentActionPreview(
        AgentIntent intent,
        String title,
        String message,
        String suggestedAction,
        Integer minutesSaved,
        List<String> affectedStops,
        String routeSummary,
        List<String> reasons,
        boolean requiresConfirmation
) {
}
