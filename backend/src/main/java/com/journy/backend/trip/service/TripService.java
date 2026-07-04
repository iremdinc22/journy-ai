package com.journy.backend.trip.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.service.ItineraryGenerationService;
import com.journy.backend.trip.dto.CreateTripRequest;
import com.journy.backend.trip.dto.TripResponse;
import com.journy.backend.trip.mapper.TripMapper;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    private final TripRepository tripRepository;
    private final ItineraryGenerationService itineraryGenerationService;
    private final TripMapper tripMapper;
    private final CurrentUserService currentUserService;

    public TripService(
            TripRepository tripRepository,
            ItineraryGenerationService itineraryGenerationService,
            TripMapper tripMapper,
            CurrentUserService currentUserService
    ) {
        this.tripRepository = tripRepository;
        this.itineraryGenerationService = itineraryGenerationService;
        this.tripMapper = tripMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public TripResponse currentTrip() {
        UserAccount user = currentUserService.currentUser();
        Trip trip = tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        UserAccount user = currentUserService.currentUser();

        tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .ifPresent(current -> {
                    current.setCurrentTrip(false);
                    tripRepository.save(current);
                });

        Trip trip = new Trip(
                user,
                request.destination(),
                request.startingArea(),
                request.startDate(),
                request.endDate(),
                request.travelerType(),
                request.budget(),
                request.pace(),
                request.interests()
        );
        trip.setCurrentTrip(true);
        trip.setTotalStops(0);
        trip.setFoodPicks(0);
        trip.setAverageWalkKm(0);

        Trip savedTrip = tripRepository.save(trip);
        itineraryGenerationService.generateIfMissing(savedTrip);
        return tripMapper.toResponse(tripRepository.save(savedTrip));
    }

    @Transactional
    public TripResponse generatePlan(String tripId) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = tripRepository.findById(tripId)
                .filter(foundTrip -> foundTrip.getUser().getId().equals(user.getId()))
                .orElseGet(() -> tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                        .orElseThrow(() -> new ResourceNotFoundException("Trip was not found")));

        itineraryGenerationService.generateIfMissing(trip);
        return tripMapper.toResponse(tripRepository.save(trip));
    }
}
