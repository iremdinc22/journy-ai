package com.journy.backend.ai.dto;

public record AiChatResponse(
        String conversationId,
        String message,
        String suggestedAction,
        Integer minutesSaved
) {
}
