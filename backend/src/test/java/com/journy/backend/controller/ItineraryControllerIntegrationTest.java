package com.journy.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.auth.dto.RegisterRequest;
import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItineraryControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void addPlaceToDayAddsStopToGeneratedItinerary() throws Exception {
        String token = registerAndGetToken("itinerary-" + System.nanoTime() + "@journy.app");
        String tripId = createTripAndGetId(token);
        generatePlan(token, tripId);

        AddPlaceToPlanRequest request = new AddPlaceToPlanRequest(
                "plc_test_cafe",
                "Quiet Test Cafe",
                "Amsterdam",
                "COFFEE",
                "A calm coffee stop added from Explore.",
                "Mid",
                4.7,
                "Amsterdam city center",
                52.3676,
                4.9041,
                "08:00 - 18:00",
                45,
                "coffee,walkable"
        );

        mockMvc.perform(post("/api/trips/{tripId}/itinerary/days/{dayNumber}/stops", tripId, 1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopCount").value(5))
                .andExpect(jsonPath("$.stops[*].title", hasItem("Quiet Test Cafe")));
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Itinerary User", email, "secret123"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private String createTripAndGetId(String token) throws Exception {
        Map<String, Object> trip = Map.of(
                "destination", "Amsterdam",
                "startingArea", "Centraal Station",
                "startDate", LocalDate.of(2026, 10, 10).toString(),
                "endDate", LocalDate.of(2026, 10, 14).toString(),
                "travelerType", "COUPLE",
                "budget", "BALANCED",
                "pace", "BALANCED",
                "interests", Set.of("COFFEE", "MUSEUMS", "LOCAL_FOOD", "WALKING")
        );

        String body = mockMvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trip)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asText();
    }

    private void generatePlan(String token, String tripId) throws Exception {
        mockMvc.perform(post("/api/trips/{tripId}/generate", tripId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
