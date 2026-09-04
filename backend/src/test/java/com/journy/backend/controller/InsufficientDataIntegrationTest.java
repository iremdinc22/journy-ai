package com.journy.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.auth.dto.RegisterRequest;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.journy.backend.support.VerifiedPlaceFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"journy.places.osm.enabled=false", "journy.destinations.nominatim.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:phase10_contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsufficientDataIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlaceRepository places;
    @Autowired TripRepository trips;
    @Autowired ItineraryDayRepository days;

    @Test
    void failedCreationRollsBackTripAndPreservesCurrentTripAndHasNoFakeStops() throws Exception {
        String token = register();
        seed("Amsterdam", "success");
        String id = create(token, "Amsterdam", 1).get("id").asText();
        long tripCount = trips.count();
        long dayCount = days.count();
        // Other-city cache and same-city seed rows must not rescue an unsupported destination.
        places.save(new com.journy.backend.explore.model.Place("Seed filler", "Edinburgh", PlaceCategory.CULTURE, "", "Mid", 4, ""));
        mvc.perform(post("/api/trips").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(draft("Edinburgh", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_DESTINATION_DATA"))
                .andExpect(jsonPath("$.details[0]").value("requiredPlaces=4"))
                .andExpect(jsonPath("$.details[1]").value("availablePlaces=0"));
        assertThat(trips.count()).isEqualTo(tripCount);
        assertThat(days.count()).isEqualTo(dayCount);
        mvc.perform(get("/api/trips/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void failedRegenerationKeepsPersistedItineraryAndTitles() throws Exception {
        String token = register(); seed("Copenhagen", "regen");
        String id = create(token, "Copenhagen", 1).get("id").asText();
        String before = itinerary(token, id);
        var trip = trips.findById(id).orElseThrow();
        trip.setEndDate(trip.getStartDate().plusDays(20)); trips.save(trip);
        mvc.perform(post("/api/trips/{id}/generate", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_DESTINATION_DATA"));
        assertThat(itinerary(token, id)).isEqualTo(before);
    }

    @Test
    void syntheticAssistantMutationsAndUnknownManualAdditionCannotCreateFakeStops() throws Exception {
        String token = register(); seed("Paris", "edits");
        String id = create(token, "Paris", 1).get("id").asText();
        String before = itinerary(token, id);
        for (String action : new String[]{"food", "replace", "rain", "budget"}) {
            mvc.perform(post("/api/ai/itinerary-apply").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("tripId", id, "dayNumber", 1, "action", action))))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INSUFFICIENT_DESTINATION_DATA"));
        }
        for (String intent : new String[]{"ADD_FOOD_STOP", "REPLACE_STOP", "RAIN_REPLAN", "BUDGET_OPTIMIZE"}) {
            mvc.perform(post("/api/agent/apply").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("tripId", id, "dayNumber", 1, "intent", intent))))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(post("/api/trips/{id}/itinerary/days/1/stops", id).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("placeId", "invented", "name", "Fake Cafe", "city", "Paris", "category", "COFFEE",
                        "description", "Fake", "priceLevel", "Mid", "rating", 4))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(itinerary(token, id)).isEqualTo(before);
    }

    private void seed(String city, String prefix) {
        for (int i = 0; i < 12; i++) places.save(place(prefix + i, "Verified " + i, city, i % 2 == 0 ? PlaceCategory.CULTURE : PlaceCategory.COFFEE));
    }
    private String register() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest("Phase 10", UUID.randomUUID() + "@example.test", "secret123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    private Map<String, Object> draft(String city, int length) {
        return Map.of("destination", city, "startingArea", "", "startDate", LocalDate.of(2026, 10, 10).toString(),
                "endDate", LocalDate.of(2026, 10, 10).plusDays(length).toString(), "travelerType", "SOLO", "budget", "BALANCED",
                "pace", "BALANCED", "interests", Set.of("CULTURE", "COFFEE"));
    }
    private JsonNode create(String token, String city, int length) throws Exception {
        return json.readTree(mvc.perform(post("/api/trips").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(draft(city, length))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String itinerary(String token, String id) throws Exception {
        return mvc.perform(get("/api/trips/{id}/itinerary", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
