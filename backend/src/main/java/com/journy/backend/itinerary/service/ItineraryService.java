package com.journy.backend.itinerary.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItineraryService {
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryMapper itineraryMapper;
    private final CurrentUserService currentUserService;

    public ItineraryService(
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            CurrentUserService currentUserService
    ) {
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public ItineraryResponse getItinerary(String tripId) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = tripRepository.findById(tripId)
                .filter(foundTrip -> foundTrip.getUser().getId().equals(user.getId()))
                .orElseGet(() -> tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                        .orElseThrow(() -> new ResourceNotFoundException("Trip was not found")));
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        return itineraryMapper.toResponse(trip, days);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse addPlaceToDay(String tripId, int dayNumber, AddPlaceToPlanRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = tripRepository.findById(tripId)
                .filter(foundTrip -> foundTrip.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == dayNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        boolean alreadyAdded = day.getStops().stream()
                .anyMatch(stop -> stop.getTitle().equalsIgnoreCase(request.name()));
        if (!alreadyAdded) {
            int nextOrder = day.getStops().size() + 1;
            ItineraryStop stop = new ItineraryStop(
                    nextOrder,
                    request.name(),
                    normalizeCategory(request.category()),
                    timeWindowFor(request.category(), nextOrder),
                    noteFor(request),
                    request.latitude() == null ? fallbackLatitude(trip.getDestination(), nextOrder) : request.latitude(),
                    request.longitude() == null ? fallbackLongitude(trip.getDestination(), nextOrder) : request.longitude()
            );
            day.addStop(stop);
            day.setSummary(summaryWithAddedPlace(day.getSummary(), request.name()));
            day.setWalkKm(Math.round((day.getWalkKm() + walkDeltaFor(request.category())) * 10.0) / 10.0);
        }

        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    private void refreshTripStats(Trip trip) {
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        int totalStops = days.stream().mapToInt(day -> day.getStops().size()).sum();
        int foodPicks = (int) days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .count();
        double averageWalk = days.stream().mapToDouble(ItineraryDay::getWalkKm).average().orElse(0);
        trip.setTotalStops(totalStops);
        trip.setFoodPicks(foodPicks);
        trip.setAverageWalkKm(Math.round(averageWalk * 10.0) / 10.0);
        tripRepository.save(trip);
    }

    private String normalizeCategory(String category) {
        return category == null ? "WALKING" : category.trim().toUpperCase().replace(" ", "_");
    }

    private String timeWindowFor(String category, int order) {
        String normalized = normalizeCategory(category);
        if (normalized.contains("COFFEE")) {
            return order <= 2 ? "11:00" : "16:00";
        }
        if (normalized.contains("FOOD")) {
            return order >= 4 ? "19:00" : "13:00";
        }
        if (normalized.contains("CULTURE")) {
            return order <= 2 ? "10:30" : "14:30";
        }
        return order >= 4 ? "17:00" : "12:00";
    }

    private String noteFor(AddPlaceToPlanRequest request) {
        return "Added from Explore because it fits the route: " + request.description();
    }

    private String summaryWithAddedPlace(String summary, String placeName) {
        if (summary.contains(placeName)) {
            return summary;
        }
        return summary + " Added " + placeName + " as a flexible stop from Explore.";
    }

    private double walkDeltaFor(String category) {
        String normalized = normalizeCategory(category);
        if (normalized.contains("FOOD") || normalized.contains("COFFEE")) {
            return 0.4;
        }
        if (normalized.contains("CULTURE")) {
            return 0.6;
        }
        return 0.5;
    }

    private double fallbackLatitude(String city, int order) {
        double base = switch (city.toLowerCase()) {
            case "paris" -> 48.8566;
            case "rome" -> 41.9028;
            case "barcelona" -> 41.3874;
            case "london" -> 51.5072;
            case "lisbon" -> 38.7223;
            case "prague" -> 50.0755;
            case "vienna" -> 48.2082;
            default -> 52.3676;
        };
        return base + order * 0.0012;
    }

    private double fallbackLongitude(String city, int order) {
        double base = switch (city.toLowerCase()) {
            case "paris" -> 2.3522;
            case "rome" -> 12.4964;
            case "barcelona" -> 2.1686;
            case "london" -> -0.1276;
            case "lisbon" -> -9.1393;
            case "prague" -> 14.4378;
            case "vienna" -> 16.3738;
            default -> 4.9041;
        };
        return base + order * 0.0012;
    }
}
