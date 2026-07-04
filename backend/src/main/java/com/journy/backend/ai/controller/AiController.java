package com.journy.backend.ai.controller;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.ai.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiService.chat(request);
    }

    @PostMapping("/itinerary-suggestion")
    public AiItinerarySuggestionResponse itinerarySuggestion(@Valid @RequestBody AiItinerarySuggestionRequest request) {
        return aiService.itinerarySuggestion(request);
    }
}
