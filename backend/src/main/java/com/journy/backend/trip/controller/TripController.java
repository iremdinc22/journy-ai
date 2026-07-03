package com.journy.backend.trip.controller;

import com.journy.backend.trip.dto.CreateTripRequest;
import com.journy.backend.trip.dto.TripResponse;
import com.journy.backend.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping("/current")
    public TripResponse currentTrip() {
        return tripService.currentTrip();
    }

    @PostMapping
    public TripResponse createTrip(@Valid @RequestBody CreateTripRequest request) {
        return tripService.createTrip(request);
    }

    @PostMapping("/{tripId}/generate")
    public TripResponse generatePlan(@PathVariable String tripId) {
        return tripService.generatePlan(tripId);
    }
}
