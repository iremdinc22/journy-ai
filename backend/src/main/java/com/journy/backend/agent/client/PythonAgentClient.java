package com.journy.backend.agent.client;

import com.journy.backend.agent.dto.AgentContext;
import com.journy.backend.agent.dto.AgentMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class PythonAgentClient {
    private final RestClient restClient;

    public PythonAgentClient(@Value("${app.ai-agent.base-url:http://localhost:8001}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Optional<AgentMessageResponse> message(String message, AgentContext context) {
        try {
            AgentMessageResponse response = restClient.post()
                    .uri("/v1/agent/message")
                    .body(new PythonAgentMessageRequest(
                            message,
                            context.trip(),
                            context.day(),
                            context.itineraryDays(),
                            context.userProfile()
                    ))
                    .retrieve()
                    .body(AgentMessageResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private record PythonAgentMessageRequest(
            String message,
            AgentContext.TripAgentContext trip,
            AgentContext.DayAgentContext day,
            java.util.List<AgentContext.DayAgentContext> itineraryDays,
            AgentContext.UserAgentContext userProfile
    ) {
    }
}
