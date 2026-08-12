package com.journy.backend.agent.service;

import com.journy.backend.agent.client.PythonAgentClient;
import com.journy.backend.agent.dto.AgentActionPreview;
import com.journy.backend.agent.dto.AgentContext;
import com.journy.backend.agent.dto.AgentApplyRequest;
import com.journy.backend.agent.dto.AgentMessageRequest;
import com.journy.backend.agent.dto.AgentMessageResponse;
import com.journy.backend.agent.enums.AgentIntent;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.ai.service.AiService;
import com.journy.backend.common.exception.ResourceNotFoundException;
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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AgentService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final AiService aiService;
    private final PythonAgentClient pythonAgentClient;
    private final AgentContextBuilder agentContextBuilder;
    private final ItineraryMapper itineraryMapper;

    public AgentService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            AiService aiService,
            PythonAgentClient pythonAgentClient,
            AgentContextBuilder agentContextBuilder,
            ItineraryMapper itineraryMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.aiService = aiService;
        this.pythonAgentClient = pythonAgentClient;
        this.agentContextBuilder = agentContextBuilder;
        this.itineraryMapper = itineraryMapper;
    }

    @Transactional(readOnly = true)
    public AgentMessageResponse message(AgentMessageRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId());
        List<ItineraryDay> itineraryDays = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        ItineraryDay day = resolveDay(itineraryDays, request.dayNumber(), request.message());
        AgentContext context = agentContextBuilder.build(user, trip, day, itineraryDays);
        AgentIntent intent = detectIntent(request.message());
        if (intent != AgentIntent.GENERAL_GUIDANCE) {
            AgentActionPreview preview = buildPreview(intent, trip, day, itineraryDays, context);
            return new AgentMessageResponse(
                    "agent_" + trip.getId(),
                    buildAgentMessage(intent, trip, day, preview, itineraryDays, context),
                    intent,
                    preview
            );
        }

        AgentMessageResponse pythonResponse = pythonAgentClient.message(request.message(), context).orElse(null);
        if (pythonResponse != null) {
            return new AgentMessageResponse(
                    "agent_" + trip.getId(),
                    pythonResponse.message(),
                    pythonResponse.intent(),
                    pythonResponse.preview()
            );
        }

        AgentActionPreview preview = buildPreview(intent, trip, day, itineraryDays, context);

        return new AgentMessageResponse(
                "agent_" + trip.getId(),
                buildAgentMessage(intent, trip, day, preview, itineraryDays, context),
                intent,
                preview
        );
    }

    @Transactional
    public ItineraryResponse.ItineraryDayResponse apply(AgentApplyRequest request) {
        AgentIntent intent = request.intent();
        if (intent == AgentIntent.GENERAL_GUIDANCE) {
            intent = AgentIntent.MAKE_DAY_LIGHTER;
        }
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId());
        ItineraryDay day = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(foundDay -> foundDay.getDayNumber() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));

        switch (intent) {
            case MAKE_DAY_LIGHTER -> applyMakeDayLighter(day);
            case ADD_FOOD_STOP -> applyAddFoodStop(trip, day);
            case REPLACE_STOP -> applyReplaceStop(trip, day);
            case BUDGET_OPTIMIZE -> applyBudgetOptimize(trip, day);
            case RAIN_REPLAN -> applyRainReplan(trip, day);
            case GENERAL_GUIDANCE -> applyMakeDayLighter(day);
        }

        normalizeStopOrder(day);
        ItineraryDay savedDay = itineraryDayRepository.save(day);
        refreshTripStats(trip);
        return itineraryMapper.toDayResponse(savedDay);
    }

    private AgentActionPreview buildPreview(AgentIntent intent, Trip trip, ItineraryDay day, List<ItineraryDay> itineraryDays, AgentContext context) {
        if (intent == AgentIntent.BUDGET_OPTIMIZE) {
            return budgetPreview(trip, day, context);
        }
        if (intent == AgentIntent.RAIN_REPLAN) {
            return rainPreview(trip, day, context);
        }
        if (intent == AgentIntent.GENERAL_GUIDANCE) {
            return guidancePreview(trip, day, context);
        }

        AiItinerarySuggestionResponse suggestion = aiService.itinerarySuggestion(new AiItinerarySuggestionRequest(
                trip.getId(),
                day.getDayNumber(),
                actionFor(intent)
        ));

        return new AgentActionPreview(
                intent,
                suggestion.title(),
                suggestion.message(),
                suggestion.suggestedAction(),
                suggestion.minutesSaved(),
                suggestion.stopsAffected(),
                suggestion.routeSummary(),
                explain(intent, trip, day, suggestion.stopsAffected(), itineraryDays, context),
                true
        );
    }

    private AgentActionPreview budgetPreview(Trip trip, ItineraryDay day, AgentContext context) {
        List<String> affectedStops = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("FOOD") || stop.getCategory().equalsIgnoreCase("COFFEE"))
                .map(ItineraryStop::getTitle)
                .limit(2)
                .toList();
        return new AgentActionPreview(
                AgentIntent.BUDGET_OPTIMIZE,
                "Optimize Day " + day.getDayNumber() + " for budget",
                "I can keep the strongest anchors, reduce expensive food pressure and replace one flexible stop with a lower-cost local option.",
                "Replace one flexible stop with a budget-friendly alternative",
                8,
                affectedStops,
                trip.getDestination() + " Day " + day.getDayNumber() + " becomes easier on budget without changing the main route shape.",
                List.of(
                        "Your trip budget is " + trip.getBudget().name().toLowerCase().replace('_', ' '),
                        personalizationReason(context),
                        "Food and coffee stops are the easiest places to optimize without losing the city experience",
                        "The route can stay close to the existing stop cluster"
                ).stream().distinct().limit(3).toList(),
                true
        );
    }

    private AgentActionPreview rainPreview(Trip trip, ItineraryDay day, AgentContext context) {
        List<String> outdoorStops = day.getStops().stream()
                .filter(stop -> stop.getCategory().equalsIgnoreCase("WALKING") || stop.getCategory().equalsIgnoreCase("FREE"))
                .map(ItineraryStop::getTitle)
                .limit(2)
                .toList();
        return new AgentActionPreview(
                AgentIntent.RAIN_REPLAN,
                "Rebuild Day " + day.getDayNumber() + " around rain",
                "I can protect the route by swapping the most weather-sensitive stop for an indoor culture or cafe window.",
                "Replace outdoor stop with indoor-friendly option",
                12,
                outdoorStops,
                trip.getDestination() + " Day " + day.getDayNumber() + " keeps the same rhythm with less weather risk.",
                List.of(
                        "Outdoor walking is the most weather-sensitive part of this day",
                        personalizationReason(context),
                        "Indoor culture and cafe stops preserve the experience in rain",
                        "Keeping the same area avoids unnecessary transfers"
                ).stream().distinct().limit(3).toList(),
                true
        );
    }

    private AgentActionPreview guidancePreview(Trip trip, ItineraryDay day, AgentContext context) {
        return new AgentActionPreview(
                AgentIntent.GENERAL_GUIDANCE,
                "I can adjust this day",
                "Tell me if you want the day lighter, cheaper, more food-focused or rebuilt around rain.",
                "Ask for a route adjustment",
                null,
                List.of(),
                trip.getDestination() + " Day " + day.getDayNumber() + " has " + day.getStops().size() + " stops and " + day.getWalkKm() + " km of walking.",
                List.of(
                        "I can read your current itinerary",
                        personalizationReason(context),
                        "I can produce a preview before changing the plan",
                        "I only apply changes after you confirm"
                ).stream().distinct().limit(3).toList(),
                false
        );
    }

    private String buildAgentMessage(
            AgentIntent intent,
            Trip trip,
            ItineraryDay day,
            AgentActionPreview preview,
            List<ItineraryDay> itineraryDays,
            AgentContext context
    ) {
        if (!preview.requiresConfirmation()) {
            return preview.message();
        }
        ItineraryDay busiestDay = findBusiestDay(itineraryDays);
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                    ? "I checked the full trip. Day " + day.getDayNumber() + " carries the most pressure with "
                    + day.getWalkKm() + " km of walking and " + day.getStops().size()
                    + " stops, so I prepared a lighter version while keeping the main anchors and your "
                    + primaryTaste(context) + " preference."
                    : "I checked Day " + day.getDayNumber() + ". I can make it lighter by reducing pressure around the optional stop and keeping the main anchors.";
            case ADD_FOOD_STOP -> "I found a way to add a local food break without stretching the route too much, shaped around your " + primaryTaste(context) + " signal.";
            case REPLACE_STOP -> "I can replace the weakest-fit stop while preserving the same route area and your saved-place preferences.";
            case BUDGET_OPTIMIZE -> "I can make this day more budget-friendly by adjusting flexible food or activity stops.";
            case RAIN_REPLAN -> "I can rebuild the day around rain by moving the route toward indoor-friendly stops.";
            case GENERAL_GUIDANCE -> preview.message();
        };
    }

    private List<String> explain(
            AgentIntent intent,
            Trip trip,
            ItineraryDay day,
            List<String> affectedStops,
            List<ItineraryDay> itineraryDays,
            AgentContext context
    ) {
        String affected = affectedStops.isEmpty() ? "the flexible route window" : affectedStops.getFirst();
        ItineraryDay busiestDay = findBusiestDay(itineraryDays);
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> List.of(
                    affected + " is the easiest place to reduce effort",
                    "Day " + day.getDayNumber() + " is currently " + day.getWalkKm() + " km of walking",
                    personalizationReason(context),
                    busiestDay.getDayNumber() == day.getDayNumber() && itineraryDays.size() > 1
                            ? "This is the busiest day in the current trip"
                            : "The core " + trip.getDestination() + " anchors stay in the plan"
            ).stream().distinct().limit(3).toList();
            case ADD_FOOD_STOP -> List.of(
                    "A food break matches your local discovery goal",
                    personalizationReason(context),
                    "It can sit near the existing route cluster",
                    "It improves pacing without rebuilding the whole day"
            ).stream().distinct().limit(3).toList();
            case REPLACE_STOP -> List.of(
                    affected + " can be swapped without breaking route order",
                    savedPlaceReason(context),
                    "The replacement stays in the same time window",
                    "This keeps the day aligned with your " + trip.getPace().name().toLowerCase() + " pace"
            ).stream().distinct().limit(3).toList();
            default -> List.of(
                    "This change uses the current itinerary context",
                    "It keeps route distance and stop order in mind",
                    "You approve before Journy changes the plan"
            );
        };
    }

    private String personalizationReason(AgentContext context) {
        return "This fits your " + primaryTaste(context) + " signal";
    }

    private String savedPlaceReason(AgentContext context) {
        List<String> savedSignals = context.userProfile().savedCategorySignals();
        if (savedSignals == null || savedSignals.isEmpty()) {
            return "The replacement can follow your current TripSetup preferences";
        }
        return "Your saved places lean toward " + savedSignals.getFirst().replace(" x", " choices x");
    }

    private String primaryTaste(AgentContext context) {
        List<String> signals = context.userProfile().tasteSignals();
        if (signals == null || signals.isEmpty()) {
            return "balanced travel";
        }
        return signals.getFirst().toLowerCase();
    }

    private AgentIntent detectIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "budget", "cheap", "cheaper", "save money", "ucuz", "bütçe", "tasarruf", "euro")) {
            return AgentIntent.BUDGET_OPTIMIZE;
        }
        if (containsAny(text, "rain", "weather", "rainy", "indoor", "inside", "covered", "rain-ready", "yağmur", "hava", "kapalı")) {
            return AgentIntent.RAIN_REPLAN;
        }
        if (containsAny(text, "coffee", "cafe", "food", "dinner", "restaurant", "kahve", "yemek", "akşam")) {
            return AgentIntent.ADD_FOOD_STOP;
        }
        if (containsAny(text, "replace", "swap", "change stop", "değiştir", "yerine")) {
            return AgentIntent.REPLACE_STOP;
        }
        if (containsAny(text, "light", "lighter", "easy", "short", "slow", "less walking", "tired", "finish earlier", "hafif", "yorul", "yorgun", "kolay", "az yür", "erken bit")) {
            return AgentIntent.MAKE_DAY_LIGHTER;
        }
        return AgentIntent.GENERAL_GUIDANCE;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String actionFor(AgentIntent intent) {
        return switch (intent) {
            case ADD_FOOD_STOP -> "food";
            case RAIN_REPLAN -> "rain";
            case BUDGET_OPTIMIZE -> "budget";
            case REPLACE_STOP -> "replace";
            default -> "lighter";
        };
    }

    private void applyMakeDayLighter(ItineraryDay day) {
        if (day.getStops().size() <= 2) {
            day.getStops().forEach(stop -> stop.setOptionalStop(true));
            day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.7)));
            day.setSummary("Journy marked this compact day as optional-friendly so you can slow down without losing the route shape.");
            return;
        }

        ItineraryStop removable = day.getStops().stream()
                .filter(ItineraryStop::isOptionalStop)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> day.getStops().stream()
                        .filter(stop -> !isFoodOrCoffee(stop))
                        .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                        .orElse(day.getStops().getLast()));
        day.getStops().remove(removable);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - walkReductionFor(removable))));
        day.setSummary("Journy removed " + removable.getTitle() + " to make the day lighter while keeping the strongest route anchors.");
    }

    private void applyAddFoodStop(Trip trip, ItineraryDay day) {
        int insertOrder = Math.min(day.getStops().size() + 1, 3);
        shiftStopsFrom(day, insertOrder);
        ItineraryStop anchor = day.getStops().stream()
                .filter(stop -> stop.getStopOrder() == Math.max(1, insertOrder - 1))
                .findFirst()
                .orElseGet(() -> day.getStops().isEmpty() ? null : day.getStops().getFirst());
        ItineraryStop stop = new ItineraryStop(
                insertOrder,
                foodBreakTitle(trip),
                "FOOD",
                "13:00",
                "Added by Journy AI as a local food break inside the existing route window.",
                anchor == null ? 0 : anchor.getLatitude() + 0.002,
                anchor == null ? 0 : anchor.getLongitude() + 0.002
        );
        day.addStop(stop);
        day.setWalkKm(round(day.getWalkKm() + 0.4));
        day.setSummary("Journy added a food break near the current route so the day feels more local without becoming much heavier.");
    }

    private void applyReplaceStop(Trip trip, ItineraryDay day) {
        ItineraryStop target = weakestFlexibleStop(day);
        target.setTitle(replacementTitle(trip, target));
        target.setCategory(replacementCategory(target));
        target.setNote("Replaced by Journy AI to better match your route, pace and taste profile.");
        target.setTimeWindow(replacementTimeWindow(target));
        target.setOptionalStop(false);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.2)));
        day.setSummary("Journy replaced the weakest-fit stop while preserving the same route window.");
    }

    private void applyBudgetOptimize(Trip trip, ItineraryDay day) {
        ItineraryStop target = day.getStops().stream()
                .filter(this::isFoodOrCoffee)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> weakestFlexibleStop(day));
        target.setTitle(budgetTitle(trip, target));
        target.setCategory(target.getCategory().equalsIgnoreCase("COFFEE") ? "COFFEE" : "FOOD");
        target.setNote("Adjusted by Journy AI toward a lower-cost local option near the existing route.");
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.1)));
        day.setSummary("Journy kept the main anchors and made the flexible food window more budget-friendly.");
    }

    private void applyRainReplan(Trip trip, ItineraryDay day) {
        ItineraryStop target = day.getStops().stream()
                .filter(this::isOutdoor)
                .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                .orElseGet(() -> weakestFlexibleStop(day));
        target.setTitle(indoorTitle(trip));
        target.setCategory("CULTURE");
        target.setTimeWindow("14:00");
        target.setNote("Rain-aware adjustment from Journy AI: protected the afternoon with an indoor-friendly stop.");
        target.setOptionalStop(false);
        day.setWalkKm(Math.max(1.2, round(day.getWalkKm() - 0.5)));
        day.setSummary("Journy moved the day toward an indoor culture window so rainy hours do not break the route.");
    }

    private ItineraryStop weakestFlexibleStop(ItineraryDay day) {
        return day.getStops().stream()
                .filter(ItineraryStop::isOptionalStop)
                .findFirst()
                .orElseGet(() -> day.getStops().stream()
                        .max(Comparator.comparingInt(ItineraryStop::getStopOrder))
                        .orElseThrow(() -> new ResourceNotFoundException("Itinerary stop was not found")));
    }

    private void shiftStopsFrom(ItineraryDay day, int order) {
        day.getStops().stream()
                .filter(stop -> stop.getStopOrder() >= order)
                .forEach(stop -> stop.setStopOrder(stop.getStopOrder() + 1));
    }

    private void normalizeStopOrder(ItineraryDay day) {
        day.getStops().sort(Comparator.comparingInt(ItineraryStop::getStopOrder));
        for (int index = 0; index < day.getStops().size(); index++) {
            day.getStops().get(index).setStopOrder(index + 1);
        }
    }

    private void refreshTripStats(Trip trip) {
        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        int totalStops = days.stream().mapToInt(day -> day.getStops().size()).sum();
        int foodPicks = (int) days.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(this::isFoodOrCoffee)
                .count();
        double averageWalk = days.stream().mapToDouble(ItineraryDay::getWalkKm).average().orElse(0);
        trip.setTotalStops(totalStops);
        trip.setFoodPicks(foodPicks);
        trip.setAverageWalkKm(round(averageWalk));
        tripRepository.save(trip);
    }

    private boolean isFoodOrCoffee(ItineraryStop stop) {
        String category = normalize(stop.getCategory());
        return category.contains("FOOD") || category.contains("COFFEE");
    }

    private boolean isOutdoor(ItineraryStop stop) {
        String category = normalize(stop.getCategory());
        String title = normalize(stop.getTitle());
        return category.contains("WALKING")
                || category.contains("FREE")
                || title.contains("WALK")
                || title.contains("GARDEN")
                || title.contains("PARK")
                || title.contains("WATERFRONT");
    }

    private double walkReductionFor(ItineraryStop stop) {
        if (isOutdoor(stop)) {
            return 1.4;
        }
        if (isFoodOrCoffee(stop)) {
            return 0.6;
        }
        return 1.0;
    }

    private String foodBreakTitle(Trip trip) {
        return trip.getDestination() + " Local Lunch Break";
    }

    private String replacementTitle(Trip trip, ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("CULTURE")) {
            return trip.getDestination() + " Compact Culture Stop";
        }
        if (category.contains("COFFEE")) {
            return trip.getDestination() + " Quiet Coffee Window";
        }
        if (category.contains("FOOD")) {
            return trip.getDestination() + " Local Food Window";
        }
        return trip.getDestination() + " Easier Route Window";
    }

    private String replacementCategory(ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("COFFEE")) return "COFFEE";
        if (category.contains("FOOD")) return "FOOD";
        if (category.contains("CULTURE")) return "CULTURE";
        return "WALKING";
    }

    private String replacementTimeWindow(ItineraryStop target) {
        String category = normalize(target.getCategory());
        if (category.contains("COFFEE")) return "11:30";
        if (category.contains("FOOD")) return "13:00";
        if (category.contains("CULTURE")) return "14:00";
        return "16:00";
    }

    private String budgetTitle(Trip trip, ItineraryStop target) {
        if (target.getCategory().equalsIgnoreCase("COFFEE")) {
            return trip.getDestination() + " Low-Cost Coffee Break";
        }
        return trip.getDestination() + " Local Market Bite";
    }

    private String indoorTitle(Trip trip) {
        return trip.getDestination() + " Indoor Culture Window";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Trip resolveTrip(UserAccount user, String tripId) {
        if (tripId != null && !tripId.isBlank()) {
            return tripRepository.findById(tripId)
                    .filter(trip -> trip.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Trip was not found"));
        }
        return tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
    }

    private ItineraryDay resolveDay(List<ItineraryDay> itineraryDays, Integer requestedDayNumber, String message) {
        if (isTripWideQuestion(message)) {
            return findBusiestDay(itineraryDays);
        }
        int dayNumber = requestedDayNumber == null ? 1 : requestedDayNumber;
        return itineraryDays.stream()
                .filter(day -> day.getDayNumber() == dayNumber)
                .findFirst()
                .orElseGet(() -> itineraryDays.stream()
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found")));
    }

    private boolean isTripWideQuestion(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return containsAny(
                text,
                "whole trip",
                "full trip",
                "which day",
                "best day",
                "all days",
                "trip overall",
                "tüm seyahat",
                "seyahat geneli",
                "hangi gün",
                "en yoğun gün"
        );
    }

    private ItineraryDay findBusiestDay(List<ItineraryDay> itineraryDays) {
        return itineraryDays.stream()
                .max((first, second) -> {
                    int walkingCompare = Double.compare(first.getWalkKm(), second.getWalkKm());
                    if (walkingCompare != 0) {
                        return walkingCompare;
                    }
                    return Integer.compare(first.getStops().size(), second.getStops().size());
                })
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found"));
    }
}
