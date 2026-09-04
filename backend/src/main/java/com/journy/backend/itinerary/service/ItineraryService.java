package com.journy.backend.itinerary.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.common.exception.InsufficientDestinationDataException;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.feedback.model.TasteFeedbackAction;
import com.journy.backend.feedback.service.TasteFeedbackService;
import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.dto.MoveStopRequest;
import com.journy.backend.itinerary.dto.ReorderStopRequest;
import com.journy.backend.itinerary.dto.RightNowResponse;
import com.journy.backend.itinerary.dto.UpdateStopStatusRequest;
import com.journy.backend.itinerary.dto.WeatherAdjustmentResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.model.StopVisitStatus;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.weather.WeatherForecastService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ItineraryService {
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryMapper itineraryMapper;
    private final CurrentUserService currentUserService;
    private final PlaceRepository placeRepository;
    private final WeatherForecastService weatherForecastService;
    private final com.journy.backend.weather.WeatherAdjustmentService weatherAdjustments;
    private final TasteFeedbackService tasteFeedbackService;
    private final ZoneId appZone = ZoneId.of("Europe/Istanbul");

    public ItineraryService(
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            CurrentUserService currentUserService,
            PlaceRepository placeRepository,
            WeatherForecastService weatherForecastService,
            com.journy.backend.weather.WeatherAdjustmentService weatherAdjustments,
            TasteFeedbackService tasteFeedbackService
    ) {
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.currentUserService = currentUserService;
        this.placeRepository = placeRepository;
        this.weatherForecastService = weatherForecastService;
        this.weatherAdjustments = weatherAdjustments;
        this.tasteFeedbackService = tasteFeedbackService;
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

    @Transactional(readOnly = true)
    public WeatherAdjustmentResponse weatherAdjustment(String tripId) {
        Trip trip = ownedTrip(tripId);
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        return weatherAdjustments.preview(trip, days);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse applyWeatherAdjustment(String tripId, String previewId) {
        Trip trip = ownedTrip(tripId);
        var days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        var day = weatherAdjustments.apply(trip, days, previewId);
        return itineraryMapper.toDayResponse(itineraryDayRepository.save(day));
    }

    @Transactional(readOnly = true)
    public RightNowResponse rightNow(String tripId) {
        Trip trip = ownedTrip(tripId);
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        if (days.isEmpty()) {
            return new RightNowResponse(
                    false,
                    1,
                    "Nothing planned yet",
                    "Create an itinerary first so Journy can recommend what to do now.",
                    null,
                    null,
                    "Plan a trip",
                    null,
                    0,
                    0,
                    List.of("No active plan"),
                    List.of("No itinerary day was found")
            );
        }

        int dayNumber = activeDayNumber(trip);
        ItineraryDay day = days.stream()
                .filter(candidate -> candidate.getDayNumber() == dayNumber)
                .findFirst()
                .orElse(days.getFirst());
        Optional<WeatherForecastService.RainForecast> forecast = weatherForecastService.rainForecastFor(trip, day.getDayNumber());
        ItineraryStop activeStop = day.getStops().stream()
                .filter(stop -> stop.getStatus() == StopVisitStatus.ARRIVED)
                .min(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElse(null);
        if (activeStop != null) {
            int delay = delayMinutes(day);
            return new RightNowResponse(
                    true,
                    day.getDayNumber(),
                    "You are here",
                    "Finish this stop when you are ready, or skip it if you want to keep the day moving.",
                    activeStop.getTitle(),
                    activeStop.getTimeWindow() + " · current stop · " + walkHint(activeStop),
                    "Open route",
                    activeStop.getId(),
                    45,
                    delay,
                    contextFor(trip, day, forecast, delay),
                    List.of(
                            "Marked as your current stop",
                            activeStop.isOptionalStop() ? "Optional, so it can be skipped if needed" : "Part of the main route",
                            delay >= 30 ? "Schedule is behind by about " + delay + " min" : "Day is still on track"
                    )
            );
        }
        ItineraryStop nextStop = day.getStops().stream()
                .filter(stop -> !isFinished(stop))
                .min(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElse(null);
        if (nextStop == null) {
            int delay = delayMinutes(day);
            return new RightNowResponse(
                    true,
                    day.getDayNumber(),
                    "You are done for today",
                    "All planned stops are completed. Keep the rest of the day flexible.",
                    trip.getDestination() + " free evening",
                    "Flexible time",
                    "Review plan",
                    null,
                    120,
                    delay,
                    contextFor(trip, day, forecast, delay),
                    List.of("All stops for Day " + day.getDayNumber() + " are completed", "Journy keeps the evening open")
            );
        }

        int freeWindow = freeWindowMinutes(nextStop);
        int delay = delayMinutes(day);
        String category = normalizeCategory(nextStop.getCategory()).toLowerCase().replace("_", " ");
        String title = delay >= 30 ? "Adjust your next move" : "What should I do now?";
        String message = delay >= 30
                ? "You are running about " + delay + " min behind schedule. Journy recommends keeping the next stop simple."
                : "You have about " + freeWindow + " min before the next planned window.";
        String meta = nextStop.getTimeWindow() + " · " + category + " · " + walkHint(nextStop);

        return new RightNowResponse(
                true,
                day.getDayNumber(),
                title,
                message,
                nextStop.getTitle(),
                meta,
                "Go here",
                nextStop.getId(),
                freeWindow,
                delay,
                contextFor(trip, day, forecast, delay),
                List.of(
                        "Next unfinished stop in Day " + day.getDayNumber(),
                        nextStop.isOptionalStop() ? "Optional, so it can be skipped if needed" : "Part of the main route",
                        delay >= 30 ? "Schedule is behind by about " + delay + " min" : "Fits the current route rhythm"
                )
        );
    }

    private boolean isFinished(ItineraryStop stop) {
        return stop.getStatus() == StopVisitStatus.DONE || stop.getStatus() == StopVisitStatus.SKIPPED;
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse addPlaceToDay(String tripId, int dayNumber, AddPlaceToPlanRequest request) {
        currentUserService.currentUserForUpdate();
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);

        var place = placeRepository.findById(request.placeId())
                .filter(PlannerPlaceContract::verified)
                .filter(candidate -> PlannerPlaceContract.inDestination(candidate, trip.getDestination()))
                .orElseThrow(() -> new InsufficientDestinationDataException(1, 0));
        boolean alreadyAdded = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(tripId).stream()
                .flatMap(existingDay -> existingDay.getStops().stream())
                .anyMatch(stop -> place.getId().equals(stop.getPlaceId()));
        if (!alreadyAdded) {
            int nextOrder = day.getStops().size() + 1;
            String category = place.getCategory().name();
            var addedStop = new ItineraryStop(nextOrder, place.getName(), category,
                    timeWindowFor(category, nextOrder), place.getId(), "provider:" + place.getProvider(),
                    place.getDescription(), place.getLatitude(), place.getLongitude());
            day.addStop(addedStop);
            tasteFeedbackService.recordTransition(place.getId(), TasteFeedbackAction.ADDED_TO_TRIP, "Added to itinerary", "ITINERARY", addedStop.getId());
            day.setTitle(DayTitleGenerator.title(day.getStops(), "en"));
            day.setSummary(summaryWithAddedPlace(day.getSummary(), place.getName()));
            day.setWalkKm(Math.round((day.getWalkKm() + walkDeltaFor(category)) * 10.0) / 10.0);
        }

        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse removeStop(String tripId, int dayNumber, String stopId) {
        currentUserService.currentUserForUpdate();
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);
        ItineraryStop stop = stopFor(day, stopId);
        tasteFeedbackService.recordTransition(stop.getPlaceId(), TasteFeedbackAction.REMOVED_FROM_TRIP, "Removed from itinerary", "ITINERARY", stop.getId());
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
    public ItineraryResponse.ItineraryDayResponse updateStopStatus(String tripId, int dayNumber, String stopId, UpdateStopStatusRequest request) {
        currentUserService.currentUserForUpdate();
        Trip trip = ownedTrip(tripId);
        ItineraryDay day = dayFor(trip, dayNumber);
        ItineraryStop stop = stopFor(day, stopId);
        if (stop.getStatus() == request.status()) return itineraryMapper.toDayResponse(day);
        Instant now = Instant.now();
        stop.setStatus(request.status());
        if (request.status() == StopVisitStatus.ARRIVED && stop.getArrivedAt() == null) {
            stop.setArrivedAt(now);
        }
        if (request.status() == StopVisitStatus.DONE) {
            if (stop.getArrivedAt() == null) {
                stop.setArrivedAt(now);
            }
            stop.setCompletedAt(now);
            tasteFeedbackService.recordTransition(stop.getPlaceId(), TasteFeedbackAction.VISITED, "Completed itinerary stop", "ITINERARY", stop.getId());
        }
        if (request.status() == StopVisitStatus.SKIPPED) {
            stop.setCompletedAt(now);
            tasteFeedbackService.recordTransition(stop.getPlaceId(), TasteFeedbackAction.SKIPPED, "Skipped itinerary stop", "ITINERARY", stop.getId());
        }
        if (request.status() == StopVisitStatus.PLANNED) {
            stop.setArrivedAt(null);
            stop.setCompletedAt(null);
        }
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

    private int activeDayNumber(Trip trip) {
        LocalDate today = LocalDate.now(appZone);
        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            return 1;
        }
        if (today.isBefore(trip.getStartDate())) {
            return 1;
        }
        if (!today.isBefore(trip.getEndDate())) {
            return Math.max(1, trip.dayCount());
        }
        long daysFromStart = ChronoUnit.DAYS.between(trip.getStartDate(), today);
        return Math.min(Math.max(1, trip.dayCount()), (int) daysFromStart + 1);
    }

    private int freeWindowMinutes(ItineraryStop stop) {
        String timeWindow = stop.getTimeWindow();
        if (timeWindow == null || !timeWindow.matches("\\d{1,2}:\\d{2}")) {
            return 90;
        }
        String[] parts = timeWindow.split(":");
        int plannedMinute = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        int nowMinute = Instant.now().atZone(appZone).getHour() * 60 + Instant.now().atZone(appZone).getMinute();
        return Math.max(30, Math.min(180, plannedMinute - nowMinute));
    }

    private int delayMinutes(ItineraryDay day) {
        return day.getStops().stream()
                .filter(stop -> stop.getCompletedAt() != null && stop.getTimeWindow() != null && stop.getTimeWindow().matches("\\d{1,2}:\\d{2}"))
                .mapToInt(stop -> {
                    String[] parts = stop.getTimeWindow().split(":");
                    int plannedMinute = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]) + visitDurationEstimate(stop);
                    int actualMinute = stop.getCompletedAt().atZone(appZone).getHour() * 60 + stop.getCompletedAt().atZone(appZone).getMinute();
                    return Math.max(0, actualMinute - plannedMinute);
                })
                .max()
                .orElse(0);
    }

    private int visitDurationEstimate(ItineraryStop stop) {
        String category = normalizeCategory(stop.getCategory());
        if (category.contains("FOOD")) return 90;
        if (category.contains("COFFEE")) return 45;
        if (category.contains("CULTURE")) return 120;
        return 60;
    }

    private String walkHint(ItineraryStop stop) {
        int seed = Math.abs((stop.getId() + stop.getTitle()).hashCode());
        return (7 + seed % 9) + " min away";
    }

    private List<String> contextFor(
            Trip trip,
            ItineraryDay day,
            Optional<WeatherForecastService.RainForecast> forecast,
            int delay
    ) {
        String time = Instant.now().atZone(appZone).toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString();
        String weather = forecast
                .map(value -> "Rain risk " + value.rainWindow())
                .orElse("No verified rain alert");
        long finishedStops = day.getStops().stream().filter(this::isFinished).count();
        String progress = finishedStops + "/" + day.getStops().size() + " stops done";
        String timing = delay >= 30 ? delay + " min behind" : "On schedule";
        return List.of(time, weather, progress, timing, trip.getPace() + " pace");
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

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
