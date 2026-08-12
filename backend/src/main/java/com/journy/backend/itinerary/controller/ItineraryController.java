package com.journy.backend.itinerary.controller;

import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.dto.MoveStopRequest;
import com.journy.backend.itinerary.dto.ReorderStopRequest;
import com.journy.backend.itinerary.dto.WeatherAdjustmentResponse;
import com.journy.backend.itinerary.service.ItineraryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/weather-adjustment")
    public WeatherAdjustmentResponse weatherAdjustment(@PathVariable String tripId) {
        return itineraryService.weatherAdjustment(tripId);
    }

    @PostMapping("/days/{dayNumber}/stops")
    public ItineraryResponse.ItineraryDayResponse addPlaceToDay(
            @PathVariable String tripId,
            @PathVariable int dayNumber,
            @Valid @RequestBody AddPlaceToPlanRequest request
    ) {
        return itineraryService.addPlaceToDay(tripId, dayNumber, request);
    }

    @DeleteMapping("/days/{dayNumber}/stops/{stopId}")
    public ItineraryResponse.ItineraryDayResponse removeStop(
            @PathVariable String tripId,
            @PathVariable int dayNumber,
            @PathVariable String stopId
    ) {
        return itineraryService.removeStop(tripId, dayNumber, stopId);
    }

    @PatchMapping("/days/{dayNumber}/stops/{stopId}/optional")
    public ItineraryResponse.ItineraryDayResponse toggleOptional(
            @PathVariable String tripId,
            @PathVariable int dayNumber,
            @PathVariable String stopId
    ) {
        return itineraryService.toggleOptional(tripId, dayNumber, stopId);
    }

    @PostMapping("/days/{dayNumber}/stops/{stopId}/move")
    public ItineraryResponse moveStop(
            @PathVariable String tripId,
            @PathVariable int dayNumber,
            @PathVariable String stopId,
            @Valid @RequestBody MoveStopRequest request
    ) {
        return itineraryService.moveStop(tripId, dayNumber, stopId, request);
    }

    @PostMapping("/days/{dayNumber}/stops/{stopId}/reorder")
    public ItineraryResponse.ItineraryDayResponse reorderStop(
            @PathVariable String tripId,
            @PathVariable int dayNumber,
            @PathVariable String stopId,
            @Valid @RequestBody ReorderStopRequest request
    ) {
        return itineraryService.reorderStop(tripId, dayNumber, stopId, request);
    }
}
