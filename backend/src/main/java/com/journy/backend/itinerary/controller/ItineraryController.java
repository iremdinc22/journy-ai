package com.journy.backend.itinerary.controller;

import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.service.ItineraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary")
public class ItineraryController {
    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping
    public ItineraryResponse getItinerary(@PathVariable String tripId) {
        return itineraryService.getItinerary(tripId);
    }
}
