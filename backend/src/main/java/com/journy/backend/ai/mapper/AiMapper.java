package com.journy.backend.ai.mapper;

import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.model.AiConversation;
import org.springframework.stereotype.Component;

@Component
public class AiMapper {
    public AiChatResponse toChatResponse(
            AiConversation conversation,
            String assistantMessage,
            String suggestedAction,
            Integer minutesSaved
    ) {
        return new AiChatResponse(conversation.getId(), assistantMessage, suggestedAction, minutesSaved);
    }
}
