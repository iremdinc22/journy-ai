package com.journy.backend.ai.service;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.dto.AiItineraryApplyRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.ai.mapper.AiMapper;
import com.journy.backend.ai.model.AiConversation;
import com.journy.backend.ai.model.AiMessage;
import com.journy.backend.ai.repository.AiConversationRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Service
public class AiService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryMapper itineraryMapper;
    private final AiConversationRepository aiConversationRepository;
    private final AiMapper aiMapper;

    public AiService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryMapper itineraryMapper,
            AiConversationRepository aiConversationRepository,
            AiMapper aiMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryMapper = itineraryMapper;
        this.aiConversationRepository = aiConversationRepository;
        this.aiMapper = aiMapper;
    }

    @Transactional(readOnly = true)
    public AiItinerarySuggestionResponse itinerarySuggestion(AiItinerarySuggestionRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        String action = request.action().toLowerCase();
        if (action.contains("food")) {
            return buildFoodSuggestion(day, trip);
        }
        if (action.contains("replace")) {
            return buildReplaceSuggestion(day, trip);
        }
        return buildLighterSuggestion(day, trip);
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse applyItinerarySuggestion(AiItineraryApplyRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        String action = request.action().toLowerCase();
        if (action.contains("food")) {
            applyFoodStop(day, trip);
        } else if (action.contains("rain") || action.contains("weather")) {
            applyRainReplan(day, trip);
        } else if (action.contains("budget") || action.contains("cheap")) {
            applyBudgetOptimization(day, trip);
        } else if (action.contains("replace")) {
            applyReplacement(day, trip);
        } else {
            applyLighterDay(day);
        }

        normalizeStopOrder(day);
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    @Transactional
    public AiChatResponse chat(AiChatRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId()).orElse(null);
        AiConversation conversation = resolveConversation(user, trip);
        conversation.addMessage(new AiMessage("USER", request.message()));

        AiDecision decision = decide(request.message(), trip);
        conversation.addMessage(new AiMessage("ASSISTANT", decision.message()));
        AiConversation savedConversation = aiConversationRepository.save(conversation);

        return aiMapper.toChatResponse(
                savedConversation,
                decision.message(),
                decision.suggestedAction(),
                decision.minutesSaved()
        );
    }

    private AiDecision decide(String message, Trip trip) {
        String text = message.toLowerCase();
        TripContext context = buildTripContext(trip);
        if (text.contains("coffee") || text.contains("cafe")) {
            return new AiDecision(
                    "I found a quiet coffee break for " + context.destination() + ". It fits best around " + context.breakWindow() + " and keeps the route close to your " + context.paceLabel() + " pace.",
                    "Add coffee stop",
                    0
            );
        }
        if (text.contains("rain") || text.contains("weather")) {
            return new AiDecision(
                    "For " + context.destination() + ", I would move outdoor walking to the clearest window and keep the day around indoor culture, coffee and food stops. That protects the plan without rebuilding everything.",
                    "Rebuild around rain",
                    18
            );
        }
        if (text.contains("dinner") || text.contains("food")) {
            return new AiDecision(
                    "Stay near the final neighborhood for dinner in " + context.destination() + ". With a " + context.budgetLabel() + " budget, I would choose a local-first place close to the last stop instead of adding a long transfer.",
                    "Suggest dinner area",
                    12
            );
        }
        if (text.contains("light") || text.contains("easy") || text.contains("short")) {
            return new AiDecision(
                    "I would keep the strongest anchor stop, remove one optional stop and add a longer break after lunch. Your " + context.stopSummary() + " plan stays realistic, but the day feels lighter.",
                    "Lighten day",
                    22
            );
        }

        return new AiDecision(
                "I can help with that for " + context.destination() + ". I would keep the main anchor stops, reduce backtracking and leave one flexible window so the route still matches your " + context.paceLabel() + " style.",
                "Adjust route",
                null
        );
    }

    private TripContext buildTripContext(Trip trip) {
        if (trip == null) {
            return new TripContext("your trip", "balanced", "mid-range", "current", "current");
        }

        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        String breakWindow = days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(stop -> stop.getCategory().equalsIgnoreCase("COFFEE") || stop.getCategory().equalsIgnoreCase("FOOD"))
                .map(ItineraryStop::getTimeWindow)
                .findFirst()
                .orElse("your afternoon break");
        String stopSummary = trip.getTotalStops() > 0
                ? trip.getTotalStops() + "-stop"
                : Math.max(1, days.stream().mapToInt(day -> day.getStops().size()).sum()) + "-stop";
        return new TripContext(
                trip.getDestination(),
                trip.getPace().name().toLowerCase().replace('_', ' '),
                trip.getBudget().name().toLowerCase().replace('_', ' '),
                breakWindow,
                stopSummary
        );
    }

    private Optional<Trip> resolveTrip(UserAccount user, String tripId) {
        if (tripId != null && !tripId.isBlank()) {
            return tripRepository.findById(tripId)
                    .filter(trip -> trip.getUser().getId().equals(user.getId()));
        }
        return tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail());
    }

    private AiConversation resolveConversation(UserAccount user, Trip trip) {
        if (trip != null) {
            return aiConversationRepository
                    .findFirstByUserEmailIgnoreCaseAndTripIdOrderByUpdatedAtDesc(user.getEmail(), trip.getId())
                    .orElseGet(() -> aiConversationRepository.save(new AiConversation(user, trip, trip.getDestination() + " assistant")));
        }
        return aiConversationRepository
                .findFirstByUserEmailIgnoreCaseAndTripIsNullOrderByUpdatedAtDesc(user.getEmail())
                .orElseGet(() -> aiConversationRepository.save(new AiConversation(user, null, "Travel assistant")));
    }

    private AiItinerarySuggestionResponse buildLighterSuggestion(ItineraryDay day, Trip trip) {
        ItineraryStop affectedStop = day.getStops().isEmpty() ? null : day.getStops().getLast();
        List<String> affected = affectedStop == null ? List.of() : List.of(affectedStop.getTitle());
        int minutesSaved = Math.max(14, (int) Math.round(day.getWalkKm() * 3.4));
        return new AiItinerarySuggestionResponse(
                "Make Day " + day.getDayNumber() + " lighter",
                "Keep the strongest anchor stops and turn the final stop into an optional window. This protects the main experience while reducing pressure.",
                "Remove optional final stop",
                minutesSaved,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " becomes a calmer " + Math.max(2, day.getStops().size() - 1) + "-stop route."
        );
    }

    private AiItinerarySuggestionResponse buildFoodSuggestion(ItineraryDay day, Trip trip) {
        ItineraryStop anchor = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .findFirst()
                .orElse(day.getStops().isEmpty() ? null : day.getStops().get(Math.min(1, day.getStops().size() - 1)));
        List<String> affected = anchor == null ? List.of() : List.of(anchor.getTitle());
        return new AiItinerarySuggestionResponse(
                "Add a nearby food break",
                "Add one local food or coffee stop near the current route instead of sending you across the city.",
                "Add food stop near route",
                0,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " keeps the same route shape with a better break window."
        );
    }

    private AiItinerarySuggestionResponse buildReplaceSuggestion(ItineraryDay day, Trip trip) {
        ItineraryStop affectedStop = day.getStops().size() > 2 ? day.getStops().get(1) : day.getStops().stream().findFirst().orElse(null);
        List<String> affected = affectedStop == null ? List.of() : List.of(affectedStop.getTitle());
        return new AiItinerarySuggestionResponse(
                "Replace one stop",
                "Swap the weakest-fit stop for another option in the same area so the day changes without breaking the route.",
                "Replace stop in same area",
                10,
                affected,
                trip.getDestination() + " Day " + day.getDayNumber() + " stays walkable while matching your preferences more closely."
        );
    }

    private void applyLighterDay(ItineraryDay day) {
        day.setTitle(lightTitle(day.getTitle()));
        if (day.getStops().size() <= 2) {
            day.setSummary("Journy kept the core stops and marked the pace as already light.");
            day.setWalkKm(Math.max(2.4, Math.round((day.getWalkKm() - 0.4) * 10.0) / 10.0));
            return;
        }
        day.getStops().removeLast();
        day.setSummary("A lighter version of the day with the final optional stop removed and more room between anchors.");
        day.setWalkKm(Math.max(2.4, Math.round((day.getWalkKm() - 1.1) * 10.0) / 10.0));
    }

    private String lightTitle(String title) {
        return title.toLowerCase().startsWith("lighter ") ? title : "Lighter " + title;
    }

    private void applyFoodStop(ItineraryDay day, Trip trip) {
        int insertIndex = bestBreakInsertIndex(day);
        int order = insertIndex + 1;
        ItineraryStop anchor = day.getStops().isEmpty()
                ? null
                : day.getStops().get(Math.max(0, Math.min(insertIndex - 1, day.getStops().size() - 1)));
        ItineraryStop stop = new ItineraryStop(
                order,
                foodStopTitle(trip),
                foodStopCategory(trip),
                foodTimeWindow(order),
                "Added by Journy inside the current route window so the day has a better local break without a long transfer.",
                coordinateNear(anchor, true, order),
                coordinateNear(anchor, false, order)
        );
        day.getStops().add(insertIndex, stop);
        stop.setDay(day);
        day.setSummary("Updated with a local break placed inside the route window while keeping the day walkable.");
        day.setWalkKm(Math.round((day.getWalkKm() + 0.5) * 10.0) / 10.0);
    }

    private void applyReplacement(ItineraryDay day, Trip trip) {
        if (day.getStops().isEmpty()) {
            applyFoodStop(day, trip);
            return;
        }
        int index = day.getStops().size() > 2 ? 1 : 0;
        ItineraryStop oldStop = day.getStops().remove(index);
        ItineraryStop replacement = new ItineraryStop(
                oldStop.getStopOrder(),
                trip.getDestination() + " better-fit stop",
                oldStop.getCategory(),
                oldStop.getTimeWindow(),
                "Replaced by Journy with a better-fit option in the same route window.",
                oldStop.getLatitude(),
                oldStop.getLongitude()
        );
        day.getStops().add(index, replacement);
        replacement.setDay(day);
        day.setSummary("Updated with one better-fit stop while preserving the original route shape.");
    }

    private void applyRainReplan(ItineraryDay day, Trip trip) {
        if (day.getStops().isEmpty()) {
            applyFoodStop(day, trip);
            day.setSummary("Updated with a covered food or cafe window so the route is safer in rain.");
            return;
        }

        int index = weatherSensitiveStopIndex(day);
        ItineraryStop oldStop = day.getStops().remove(index);
        ItineraryStop replacement = new ItineraryStop(
                oldStop.getStopOrder(),
                trip.getDestination() + " indoor culture window",
                indoorCategoryFor(oldStop),
                oldStop.getTimeWindow(),
                "Replanned by Journy for rain with a covered culture, cafe or local indoor stop in the same route window.",
                oldStop.getLatitude(),
                oldStop.getLongitude()
        );
        day.getStops().add(index, replacement);
        replacement.setDay(day);
        day.setTitle(rainTitle(day.getTitle()));
        day.setSummary("Rain-ready version of the day with the most weather-sensitive stop moved indoors while preserving route rhythm.");
        day.setWalkKm(Math.max(2.2, Math.round((day.getWalkKm() - 0.3) * 10.0) / 10.0));
    }

    private void applyBudgetOptimization(ItineraryDay day, Trip trip) {
        if (day.getStops().isEmpty()) {
            applyFoodStop(day, trip);
            return;
        }

        int index = budgetFlexibleStopIndex(day);
        ItineraryStop oldStop = day.getStops().remove(index);
        ItineraryStop replacement = new ItineraryStop(
                oldStop.getStopOrder(),
                trip.getDestination() + " local budget pick",
                budgetCategoryFor(oldStop),
                oldStop.getTimeWindow(),
                "Replaced by Journy with a lower-cost local option that keeps the same area and avoids an extra transfer.",
                oldStop.getLatitude(),
                oldStop.getLongitude()
        );
        day.getStops().add(index, replacement);
        replacement.setDay(day);
        day.setSummary("Budget-aware version of the day with one flexible stop changed to a lower-cost local option.");
        day.setWalkKm(Math.max(2.2, Math.round((day.getWalkKm() - 0.2) * 10.0) / 10.0));
    }

    private double coordinateNear(ItineraryStop anchor, boolean latitude, int order) {
        double base = anchor == null ? latitude ? 52.3676 : 4.9041 : latitude ? anchor.getLatitude() : anchor.getLongitude();
        double delta = order * 0.0015;
        return latitude ? base - delta : base + delta;
    }

    private int bestBreakInsertIndex(ItineraryDay day) {
        if (day.getStops().size() <= 1) {
            return day.getStops().size();
        }
        for (int index = 0; index < day.getStops().size() - 1; index++) {
            ItineraryStop current = day.getStops().get(index);
            ItineraryStop next = day.getStops().get(index + 1);
            if (isAnchor(current) || isAnchor(next)) {
                return index + 1;
            }
        }
        return Math.max(1, day.getStops().size() / 2);
    }

    private int weatherSensitiveStopIndex(ItineraryDay day) {
        for (int index = day.getStops().size() - 1; index >= 0; index--) {
            ItineraryStop stop = day.getStops().get(index);
            if (isOutdoor(stop)) {
                return index;
            }
        }
        return Math.max(0, day.getStops().size() - 1);
    }

    private int budgetFlexibleStopIndex(ItineraryDay day) {
        for (int index = 0; index < day.getStops().size(); index++) {
            ItineraryStop stop = day.getStops().get(index);
            if (stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE")) {
                return index;
            }
        }
        for (int index = day.getStops().size() - 1; index >= 0; index--) {
            if (!isAnchor(day.getStops().get(index))) {
                return index;
            }
        }
        return Math.max(0, day.getStops().size() - 1);
    }

    private boolean isAnchor(ItineraryStop stop) {
        return stop.getCategory().equalsIgnoreCase("MUSEUM")
                || stop.getCategory().equalsIgnoreCase("CULTURE")
                || stop.getCategory().equalsIgnoreCase("LANDMARK");
    }

    private boolean isOutdoor(ItineraryStop stop) {
        return stop.getCategory().equalsIgnoreCase("WALKING")
                || stop.getCategory().equalsIgnoreCase("FREE")
                || stop.getCategory().equalsIgnoreCase("PARK")
                || stop.getCategory().equalsIgnoreCase("LANDMARK");
    }

    private String foodStopTitle(Trip trip) {
        boolean coffee = trip.getInterests().stream().anyMatch(interest -> interest.name().equalsIgnoreCase("COFFEE"));
        return coffee ? trip.getDestination() + " coffee pause" : trip.getDestination() + " local food break";
    }

    private String foodStopCategory(Trip trip) {
        boolean coffee = trip.getInterests().stream().anyMatch(interest -> interest.name().equalsIgnoreCase("COFFEE"));
        return coffee ? "COFFEE" : "FOOD";
    }

    private String foodTimeWindow(int order) {
        if (order <= 2) {
            return "11:30";
        }
        if (order >= 5) {
            return "17:30";
        }
        return "14:00";
    }

    private String indoorCategoryFor(ItineraryStop oldStop) {
        if (oldStop.getCategory().equalsIgnoreCase("FOOD") || oldStop.getCategory().equalsIgnoreCase("COFFEE")) {
            return oldStop.getCategory();
        }
        return "CULTURE";
    }

    private String budgetCategoryFor(ItineraryStop oldStop) {
        if (oldStop.getCategory().equalsIgnoreCase("COFFEE")) {
            return "COFFEE";
        }
        return "FOOD";
    }

    private String rainTitle(String title) {
        return title.toLowerCase().startsWith("rain-ready ") ? title : "Rain-ready " + title;
    }

    private void normalizeStopOrder(ItineraryDay day) {
        for (int index = 0; index < day.getStops().size(); index++) {
            day.getStops().get(index).setStopOrder(index + 1);
        }
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

    private record AiDecision(String message, String suggestedAction, Integer minutesSaved) {
    }

    private record TripContext(String destination, String paceLabel, String budgetLabel, String breakWindow, String stopSummary) {
    }
}
