package com.journy.backend.trip.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryGenerationService;
import com.journy.backend.trip.dto.CreateTripRequest;
import com.journy.backend.trip.dto.TripPreviewRequest;
import com.journy.backend.trip.dto.TripPreviewResponse;
import com.journy.backend.trip.dto.TripResponse;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.trip.mapper.TripMapper;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.journy.backend.place.enums.PlaceCategory;

import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class TripService {
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final PlaceRepository placeRepository;
    private final ItineraryGenerationService itineraryGenerationService;
    private final TripMapper tripMapper;
    private final CurrentUserService currentUserService;

    public TripService(
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            PlaceRepository placeRepository,
            ItineraryGenerationService itineraryGenerationService,
            TripMapper tripMapper,
            CurrentUserService currentUserService
    ) {
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.placeRepository = placeRepository;
        this.itineraryGenerationService = itineraryGenerationService;
        this.tripMapper = tripMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public TripPreviewResponse preview(TripPreviewRequest request) {
        String destination = request.destination() == null ? "" : request.destination().trim();
        TripPace pace = request.pace() == null ? TripPace.BALANCED : request.pace();
        BudgetMode budget = request.budget() == null ? BudgetMode.BALANCED : request.budget();
        Set<TravelInterest> interests = request.interests() == null ? Set.of() : request.interests();
        int days = previewDays(request);
        int stopsPerDay = stopsPerDay(pace, budget);
        int estimatedStops = days * stopsPerDay;
        int availablePlaceCount = destination.isBlank() ? 0 : (int) placeRepository.countByCityIgnoreCase(destination);
        int matchedPlaceCount = destination.isBlank() || interests.isEmpty()
                ? availablePlaceCount
                : (int) placeRepository.countByCityIgnoreCaseAndCategoryIn(destination, categoriesFor(interests));
        double dailyWalkKm = dailyWalkKm(stopsPerDay, pace, budget, availablePlaceCount);
        String routeStyle = routeStyle(pace, budget, interests, request.startingArea(), matchedPlaceCount);
        String confidence = confidence(destination, request.startDate() != null && request.endDate() != null, interests, availablePlaceCount);
        String summary = summary(destination, days, routeStyle, availablePlaceCount, matchedPlaceCount);

        return new TripPreviewResponse(
                estimatedStops,
                dailyWalkKm,
                routeStyle,
                availablePlaceCount,
                confidence,
                summary
        );
    }

    @Transactional(readOnly = true)
    public List<TripResponse> trips() {
        UserAccount user = currentUserService.currentUser();
        return tripRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
                .stream()
                .map(tripMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse currentTrip() {
        UserAccount user = currentUserService.currentUser();
        Trip trip = tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
        return tripMapper.toResponse(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse trip(String tripId) {
        Trip trip = ownedTrip(tripId);
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

    @Transactional
    public TripResponse makeCurrent(String tripId) {
        UserAccount user = currentUserService.currentUser();
        Trip selectedTrip = ownedTrip(tripId);

        tripRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
                .forEach(trip -> trip.setCurrentTrip(trip.getId().equals(selectedTrip.getId())));

        return tripMapper.toResponse(tripRepository.save(selectedTrip));
    }

    @Transactional
    public void deleteTrip(String tripId) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = ownedTrip(tripId);
        boolean wasCurrent = trip.isCurrentTrip();

        itineraryDayRepository.deleteByTripId(trip.getId());
        tripRepository.delete(trip);

        if (wasCurrent) {
            tripRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
                    .stream()
                    .findFirst()
                    .ifPresent(nextTrip -> {
                        nextTrip.setCurrentTrip(true);
                        tripRepository.save(nextTrip);
                    });
        }
    }

    private Trip ownedTrip(String tripId) {
        UserAccount user = currentUserService.currentUser();
        return tripRepository.findById(tripId)
                .filter(foundTrip -> foundTrip.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
    }

    private int previewDays(TripPreviewRequest request) {
        if (request.startDate() == null || request.endDate() == null || request.endDate().isBefore(request.startDate())) {
            return 1;
        }
        return Math.max(1, (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()));
    }

    private int stopsPerDay(TripPace pace, BudgetMode budget) {
        int stops = switch (pace) {
            case RELAXED -> 3;
            case FULL -> 5;
            default -> 4;
        };
        if (budget == BudgetMode.COMFORT && pace != TripPace.RELAXED) {
            return stops + 1;
        }
        return stops;
    }

    private double dailyWalkKm(int stopsPerDay, TripPace pace, BudgetMode budget, int availablePlaceCount) {
        double paceBase = switch (pace) {
            case RELAXED -> 1.05;
            case FULL -> 1.52;
            default -> 1.28;
        };
        double budgetAdjustment = switch (budget) {
            case LEAN -> -0.18;
            case COMFORT -> 0.12;
            default -> 0;
        };
        double densityAdjustment = availablePlaceCount >= 12 ? -0.25 : availablePlaceCount <= 3 ? 0.35 : 0;
        double estimate = stopsPerDay * (paceBase + budgetAdjustment) + densityAdjustment;
        return Math.max(2.2, Math.round(estimate * 10.0) / 10.0);
    }

    private EnumSet<PlaceCategory> categoriesFor(Set<TravelInterest> interests) {
        EnumSet<PlaceCategory> categories = EnumSet.noneOf(PlaceCategory.class);
        if (interests.contains(TravelInterest.COFFEE)) categories.add(PlaceCategory.COFFEE);
        if (interests.contains(TravelInterest.LOCAL_FOOD)) categories.add(PlaceCategory.FOOD);
        if (interests.contains(TravelInterest.MUSEUMS) || interests.contains(TravelInterest.CULTURE)) categories.add(PlaceCategory.CULTURE);
        if (interests.contains(TravelInterest.WALKING)) categories.add(PlaceCategory.WALKING);
        if (interests.contains(TravelInterest.FREE_ACTIVITIES)) categories.add(PlaceCategory.FREE);
        if (categories.isEmpty()) categories.addAll(EnumSet.allOf(PlaceCategory.class));
        return categories;
    }

    private String routeStyle(TripPace pace, BudgetMode budget, Set<TravelInterest> interests, String startingArea, int matchedPlaceCount) {
        if (startingArea != null && !startingArea.isBlank()) return "Area-first route";
        if (pace == TripPace.RELAXED) return "Easy paced flow";
        if (budget == BudgetMode.LEAN) return "Low-cost local route";
        if (interests.contains(TravelInterest.LOCAL_FOOD) || interests.contains(TravelInterest.COFFEE)) return "Food-led discovery";
        if (interests.contains(TravelInterest.MUSEUMS) || interests.contains(TravelInterest.CULTURE)) return "Culture clustered route";
        if (matchedPlaceCount >= 8) return "High-confidence city flow";
        return "Balanced city route";
    }

    private String confidence(String destination, boolean hasDates, Set<TravelInterest> interests, int availablePlaceCount) {
        int score = 0;
        if (!destination.isBlank()) score += 1;
        if (hasDates) score += 1;
        if (!interests.isEmpty()) score += 1;
        if (availablePlaceCount >= 6) score += 1;
        if (score >= 4) return "High";
        if (score >= 2) return "Medium";
        return "Draft";
    }

    private String summary(String destination, int days, String routeStyle, int availablePlaceCount, int matchedPlaceCount) {
        if (destination == null || destination.isBlank()) {
            return "Choose a destination to estimate route quality and available local picks.";
        }
        return "%s has %d curated picks available. Journy can shape a %d-day %s with %d strong matches for your taste."
                .formatted(destination, availablePlaceCount, days, routeStyle.toLowerCase(), matchedPlaceCount);
    }
}
