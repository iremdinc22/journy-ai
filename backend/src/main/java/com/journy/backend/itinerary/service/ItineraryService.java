package com.journy.backend.itinerary.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.itinerary.dto.AddPlaceToPlanRequest;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.dto.MoveStopRequest;
import com.journy.backend.itinerary.dto.ReorderStopRequest;
import com.journy.backend.itinerary.dto.WeatherAdjustmentResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.weather.WeatherForecastService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ItineraryService {
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryMapper itineraryMapper;
    private final CurrentUserService currentUserService;
    private final DestinationCoordinateResolver destinationCoordinateResolver;
    private final WeatherForecastService weatherForecastService;

    public ItineraryService(
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            CurrentUserService currentUserService,
            DestinationCoordinateResolver destinationCoordinateResolver,
            WeatherForecastService weatherForecastService
    ) {
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.currentUserService = currentUserService;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
        this.weatherForecastService = weatherForecastService;
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
        ItineraryDay targetDay = days.stream()
                .filter(day -> day.getStops().stream().anyMatch(this::isWeatherSensitive))
                .max(Comparator.comparingInt(this::weatherSensitivityScore))
                .orElse(null);

        if (targetDay == null) {
            return new WeatherAdjustmentResponse(
                    false,
                    1,
                    null,
                    "No weather adjustment needed",
                    "This itinerary is already mostly indoor-friendly.",
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of("No outdoor-heavy stop was found")
            );
        }

        ItineraryStop affectedStop = targetDay.getStops().stream()
                .filter(this::isWeatherSensitive)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElse(targetDay.getStops().getFirst());
        ItineraryStop indoorStop = targetDay.getStops().stream()
                .filter(stop -> !isWeatherSensitive(stop))
                .findFirst()
                .orElse(null);
        String indoorAlternative = indoorStop == null
                ? trip.getDestination() + " Indoor Culture Window"
                : indoorStop.getTitle();
        double afterWalkKm = Math.max(1.2, round(targetDay.getWalkKm() - weatherWalkReduction(affectedStop)));
        Optional<WeatherForecastService.RainForecast> forecast = weatherForecastService.rainForecastFor(trip, targetDay.getDayNumber());
        String rainWindow = forecast.map(WeatherForecastService.RainForecast::rainWindow)
                .orElseGet(() -> rainWindowFor(trip, targetDay));
        String sourceReason = forecast
                .map(value -> value.source() + " forecast shows " + value.precipitationProbability() + "% precipitation risk")
                .orElse("Forecast provider was unavailable, so Journy used the route weather-risk fallback");

        return new WeatherAdjustmentResponse(
                true,
                targetDay.getDayNumber(),
                rainWindow,
                forecast.isPresent()
                        ? "Rain risk around Day " + targetDay.getDayNumber() + " at " + rainWindow
                        : "Weather-sensitive route around Day " + targetDay.getDayNumber(),
                "Journy can protect the wettest window by moving " + affectedStop.getTitle()
                        + " earlier and using " + indoorAlternative + " as the safer afternoon anchor.",
                affectedStop.getTitle(),
                indoorAlternative,
                targetDay.getStops().size(),
                targetDay.getWalkKm(),
                targetDay.getStops().size(),
                afterWalkKm,
                List.of(
                        "Move " + affectedStop.getTitle() + " out of the rain window",
                        "Keep culture, cafe or food stops for " + rainWindow,
                        "Preserve the day rhythm before applying changes"
                ),
                List.of(
                        sourceReason,
                        affectedStop.getTitle() + " is weather-sensitive",
                        "Day " + targetDay.getDayNumber() + " has " + targetDay.getWalkKm() + " km of walking",
                        "Indoor-friendly stops reduce route risk without rebuilding the whole trip"
                ).stream().limit(3).toList()
        );
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

    private int weatherSensitivityScore(ItineraryDay day) {
        return day.getStops().stream().mapToInt(stop -> isWeatherSensitive(stop) ? 2 : 0).sum()
                + (day.getWalkKm() >= 5 ? 2 : day.getWalkKm() >= 4 ? 1 : 0);
    }

    private boolean isWeatherSensitive(ItineraryStop stop) {
        String category = normalizeCategory(stop.getCategory());
        String title = normalizeCategory(stop.getTitle());
        return category.contains("WALKING")
                || category.contains("FREE")
                || title.contains("WALK")
                || title.contains("PARK")
                || title.contains("GARDEN")
                || title.contains("WATERFRONT")
                || title.contains("VIEW");
    }

    private double weatherWalkReduction(ItineraryStop stop) {
        return isWeatherSensitive(stop) ? 0.8 : 0.4;
    }

    private String rainWindowFor(Trip trip, ItineraryDay day) {
        int seed = Math.abs((trip.getDestination() + trip.getStartDate() + day.getDayNumber()).hashCode());
        return seed % 2 == 0 ? "14:00 - 17:00" : "15:00 - 18:00";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
