package com.journy.backend.agent.dto;

import com.journy.backend.agent.enums.AgentIntent;

public record AgentMessageResponse(
        String conversationId,
        String message,
        AgentIntent intent,
        AgentActionPreview preview
) {
}
