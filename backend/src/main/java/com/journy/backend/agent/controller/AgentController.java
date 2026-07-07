package com.journy.backend.agent.controller;

import com.journy.backend.agent.dto.AgentApplyRequest;
import com.journy.backend.agent.dto.AgentMessageRequest;
import com.journy.backend.agent.dto.AgentMessageResponse;
import com.journy.backend.agent.service.AgentService;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/message")
    public AgentMessageResponse message(@Valid @RequestBody AgentMessageRequest request) {
        return agentService.message(request);
    }

    @PostMapping("/apply")
    public ItineraryResponse.ItineraryDayResponse apply(@Valid @RequestBody AgentApplyRequest request) {
        return agentService.apply(request);
    }
}
