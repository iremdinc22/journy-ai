package com.journy.backend.agent.client;

import com.journy.backend.agent.dto.AgentMessageResponse;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.trip.model.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
public class PythonAgentClient {
    private final RestClient restClient;

    public PythonAgentClient(@Value("${app.ai-agent.base-url:http://localhost:8001}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Optional<AgentMessageResponse> message(String message, Trip trip, ItineraryDay day) {
        try {
            AgentMessageResponse response = restClient.post()
                    .uri("/v1/agent/message")
                    .body(toRequest(message, trip, day))
                    .retrieve()
                    .body(AgentMessageResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private PythonAgentMessageRequest toRequest(String message, Trip trip, ItineraryDay day) {
        return new PythonAgentMessageRequest(
                message,
                new PythonTripContext(
                        trip.getId(),
                        trip.getDestination(),
                        trip.getBudget().name(),
                        trip.getPace().name(),
                        trip.getInterests().stream().map(Enum::name).toList(),
                        trip.getStartingArea()
                ),
                new PythonDayContext(
                        day.getDayNumber(),
                        day.getTitle(),
                        day.getSummary(),
                        day.getWalkKm(),
                        day.getStops().size(),
                        day.getStops().stream().map(this::toStop).toList()
                )
        );
    }

    private PythonStopContext toStop(ItineraryStop stop) {
        return new PythonStopContext(
                stop.getStopOrder(),
                stop.getTitle(),
                stop.getCategory(),
                stop.getTimeWindow(),
                stop.getNote(),
                stop.getLatitude(),
                stop.getLongitude()
        );
    }

    private record PythonAgentMessageRequest(
            String message,
            PythonTripContext trip,
            PythonDayContext day
    ) {
    }

    private record PythonTripContext(
            String tripId,
            String destination,
            String budget,
            String pace,
            List<String> interests,
            String startingArea
    ) {
    }

    private record PythonDayContext(
            int dayNumber,
            String title,
            String summary,
            double walkKm,
            int stopCount,
            List<PythonStopContext> stops
    ) {
    }

    private record PythonStopContext(
            int order,
            String title,
            String category,
            String timeWindow,
            String note,
            Double latitude,
            Double longitude
    ) {
    }
}
