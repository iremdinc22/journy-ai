package com.journy.backend.trip.controller;

import com.journy.backend.trip.dto.CreateTripRequest;
import com.journy.backend.trip.dto.TripPreviewRequest;
import com.journy.backend.trip.dto.TripPreviewResponse;
import com.journy.backend.trip.dto.TripResponse;
import com.journy.backend.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripResponse> trips() {
        return tripService.trips();
    }

    @GetMapping("/current")
    public TripResponse currentTrip() {
        return tripService.currentTrip();
    }

    @PostMapping("/preview")
    public TripPreviewResponse preview(@RequestBody TripPreviewRequest request) {
        return tripService.preview(request);
    }

    @GetMapping("/{tripId}")
    public TripResponse trip(@PathVariable String tripId) {
        return tripService.trip(tripId);
    }

    @PostMapping
    public TripResponse createTrip(@Valid @RequestBody CreateTripRequest request) {
        return tripService.createTrip(request);
    }

    @PostMapping("/{tripId}/generate")
    public TripResponse generatePlan(@PathVariable String tripId) {
        return tripService.generatePlan(tripId);
    }

    @PutMapping("/{tripId}/current")
    public TripResponse makeCurrent(@PathVariable String tripId) {
        return tripService.makeCurrent(tripId);
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable String tripId) {
        tripService.deleteTrip(tripId);
    }
}
