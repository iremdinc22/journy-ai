package com.journy.backend.itinerary.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.dto.MoveStopRequest;
import com.journy.backend.itinerary.dto.ReorderStopRequest;
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
    private final DestinationCoordinateResolver destinationCoordinateResolver;

    public ItineraryService(
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            CurrentUserService currentUserService,
            DestinationCoordinateResolver destinationCoordinateResolver
    ) {
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.currentUserService = currentUserService;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
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
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);

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

    @Transactional
    public ItineraryResponse.ItineraryDayResponse removeStop(String tripId, int dayNumber, String stopId) {
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);
        ItineraryStop stop = stopFor(day, stopId);
        day.getStops().remove(stop);
        normalizeStopOrder(day);
        refreshDayAfterManualChange(day, "Removed " + stop.getTitle() + " from this day.");
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse toggleOptional(String tripId, int dayNumber, String stopId) {
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);
        ItineraryStop stop = stopFor(day, stopId);
        stop.setOptionalStop(!stop.isOptionalStop());
        refreshDayAfterManualChange(day, stop.getTitle() + (stop.isOptionalStop() ? " is now optional." : " is back in the main route."));
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    @Transactional
    public ItineraryResponse moveStop(String tripId, int dayNumber, String stopId, MoveStopRequest request) {
        Trip trip = ownedTrip(tripId);
        ItineraryDay sourceDay = dayFor(trip, dayNumber);
        ItineraryDay targetDay = dayFor(trip, request.targetDayNumber());
        ItineraryStop stop = stopFor(sourceDay, stopId);
        sourceDay.getStops().remove(stop);
        normalizeStopOrder(sourceDay);
        stop.setStopOrder(targetDay.getStops().size() + 1);
        targetDay.addStop(stop);
        refreshDayAfterManualChange(sourceDay, "Moved " + stop.getTitle() + " to Day " + targetDay.getDayNumber() + ".");
        refreshDayAfterManualChange(targetDay, "Moved " + stop.getTitle() + " into this day.");
        itineraryDayRepository.saveAll(List.of(sourceDay, targetDay));
        refreshTripStats(trip);
        return itineraryMapper.toResponse(trip, itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()));
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse reorderStop(String tripId, int dayNumber, String stopId, ReorderStopRequest request) {
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);
        ItineraryStop stop = stopFor(day, stopId);
        day.getStops().remove(stop);
        int targetIndex = Math.max(0, Math.min(request.targetOrder() - 1, day.getStops().size()));
        day.getStops().add(targetIndex, stop);
        normalizeStopOrder(day);
        refreshDayAfterManualChange(day, "Reordered " + stop.getTitle() + ". Review walking flow before heading out.");
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    private Trip ownedTrip(String tripId) {
        UserAccount user = currentUserService.currentUser();
        return tripRepository.findById(tripId)
                .filter(foundTrip -> foundTrip.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
    }

    private ItineraryDay dayFor(Trip trip, int dayNumber) {
        return itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == dayNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));
    }

    private ItineraryStop stopFor(ItineraryDay day, String stopId) {
        return day.getStops().stream()
                .filter(stop -> stop.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary stop was not found"));
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

    private void normalizeStopOrder(ItineraryDay day) {
        for (int index = 0; index < day.getStops().size(); index++) {
            day.getStops().get(index).setStopOrder(index + 1);
        }
    }

    private void refreshDayAfterManualChange(ItineraryDay day, String changeNote) {
        normalizeStopOrder(day);
        day.setWalkKm(Math.max(1.2, Math.round(day.getStops().size() * 1.18 * 10.0) / 10.0));
        day.setSummary(changeNote + " Journy can optimize the route if the walking flow feels off.");
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
        return destinationCoordinateResolver.latitudeFor(city, order);
    }

    private double fallbackLongitude(String city, int order) {
        return destinationCoordinateResolver.longitudeFor(city, order);
    }
}
