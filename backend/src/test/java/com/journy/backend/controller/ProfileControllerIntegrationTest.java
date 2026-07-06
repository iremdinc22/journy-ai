package com.journy.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.auth.dto.RegisterRequest;
import com.journy.backend.profile.dto.UpdatePreferencesRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void updatePreferencesPersistsUserDefaults() throws Exception {
        String email = "profile-" + System.nanoTime() + "@journy.app";
        String token = registerAndGetToken(email);
        createCurrentTrip(token);

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(
                "RELAXED",
                "LEAN",
                "BUDGET_FRIENDLY",
                false,
                true
        );

        mockMvc.perform(put("/api/users/me/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.defaultPace").value("RELAXED"))
                .andExpect(jsonPath("$.preferences.defaultBudget").value("LEAN"))
                .andExpect(jsonPath("$.preferences.foodDiscovery").value("BUDGET_FRIENDLY"))
                .andExpect(jsonPath("$.preferences.planChangeNotifications").value(false))
                .andExpect(jsonPath("$.preferences.foodWindowNotifications").value(true));
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Profile User", email, "secret123"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private void createCurrentTrip(String token) throws Exception {
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

        mockMvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trip)))
                .andExpect(status().isOk());
    }
}
