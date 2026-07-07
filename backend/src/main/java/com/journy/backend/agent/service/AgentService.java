package com.journy.backend.agent.service;

import com.journy.backend.agent.client.PythonAgentClient;
import com.journy.backend.agent.dto.AgentActionPreview;
import com.journy.backend.agent.dto.AgentApplyRequest;
import com.journy.backend.agent.dto.AgentMessageRequest;
import com.journy.backend.agent.dto.AgentMessageResponse;
import com.journy.backend.agent.enums.AgentIntent;
import com.journy.backend.ai.dto.AiItineraryApplyRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.ai.service.AiService;
import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.itinerary.dto.ItineraryResponse;
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
public class AgentService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final AiService aiService;
    private final PythonAgentClient pythonAgentClient;

    public AgentService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            AiService aiService,
            PythonAgentClient pythonAgentClient
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.aiService = aiService;
        this.pythonAgentClient = pythonAgentClient;
    }

    @Transactional(readOnly = true)
    public AgentMessageResponse message(AgentMessageRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId());
        ItineraryDay day = resolveDay(trip, request.dayNumber());
        AgentMessageResponse pythonResponse = pythonAgentClient.message(request.message(), trip, day).orElse(null);
        if (pythonResponse != null) {
            return new AgentMessageResponse(
                    "agent_" + trip.getId(),
                    pythonResponse.message(),
                    pythonResponse.intent(),
                    pythonResponse.preview()
            );
        }

        AgentIntent intent = detectIntent(request.message());
        AgentActionPreview preview = buildPreview(intent, trip, day);

        return new AgentMessageResponse(
                "agent_" + trip.getId(),
                buildAgentMessage(intent, trip, day, preview),
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
        return aiService.applyItinerarySuggestion(new AiItineraryApplyRequest(
                request.tripId(),
                request.dayNumber(),
                actionFor(intent)
        ));
    }

    private AgentActionPreview buildPreview(AgentIntent intent, Trip trip, ItineraryDay day) {
        if (intent == AgentIntent.BUDGET_OPTIMIZE) {
            return budgetPreview(trip, day);
        }
        if (intent == AgentIntent.RAIN_REPLAN) {
            return rainPreview(trip, day);
        }
        if (intent == AgentIntent.GENERAL_GUIDANCE) {
            return guidancePreview(trip, day);
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
                explain(intent, trip, day, suggestion.stopsAffected()),
                true
        );
    }

    private AgentActionPreview budgetPreview(Trip trip, ItineraryDay day) {
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
                        "Food and coffee stops are the easiest places to optimize without losing the city experience",
                        "The route can stay close to the existing stop cluster"
                ),
                true
        );
    }

    private AgentActionPreview rainPreview(Trip trip, ItineraryDay day) {
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
                        "Indoor culture and cafe stops preserve the experience in rain",
                        "Keeping the same area avoids unnecessary transfers"
                ),
                true
        );
    }

    private AgentActionPreview guidancePreview(Trip trip, ItineraryDay day) {
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
                        "I can produce a preview before changing the plan",
                        "I only apply changes after you confirm"
                ),
                false
        );
    }

    private String buildAgentMessage(AgentIntent intent, Trip trip, ItineraryDay day, AgentActionPreview preview) {
        if (!preview.requiresConfirmation()) {
            return preview.message();
        }
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> "I checked Day " + day.getDayNumber() + ". I can make it lighter by reducing pressure around the optional stop and keeping the main anchors.";
            case ADD_FOOD_STOP -> "I found a way to add a local food break without stretching the route too much.";
            case REPLACE_STOP -> "I can replace the weakest-fit stop while preserving the same route area.";
            case BUDGET_OPTIMIZE -> "I can make this day more budget-friendly by adjusting flexible food or activity stops.";
            case RAIN_REPLAN -> "I can rebuild the day around rain by moving the route toward indoor-friendly stops.";
            case GENERAL_GUIDANCE -> preview.message();
        };
    }

    private List<String> explain(AgentIntent intent, Trip trip, ItineraryDay day, List<String> affectedStops) {
        String affected = affectedStops.isEmpty() ? "the flexible route window" : affectedStops.getFirst();
        return switch (intent) {
            case MAKE_DAY_LIGHTER -> List.of(
                    affected + " is the easiest place to reduce effort",
                    "Day " + day.getDayNumber() + " is currently " + day.getWalkKm() + " km of walking",
                    "The core " + trip.getDestination() + " anchors stay in the plan"
            );
            case ADD_FOOD_STOP -> List.of(
                    "A food break matches your local discovery goal",
                    "It can sit near the existing route cluster",
                    "It improves pacing without rebuilding the whole day"
            );
            case REPLACE_STOP -> List.of(
                    affected + " can be swapped without breaking route order",
                    "The replacement stays in the same time window",
                    "This keeps the day aligned with your " + trip.getPace().name().toLowerCase() + " pace"
            );
            default -> List.of(
                    "This change uses the current itinerary context",
                    "It keeps route distance and stop order in mind",
                    "You approve before Journy changes the plan"
            );
        };
    }

    private AgentIntent detectIntent(String message) {
        String text = message.toLowerCase();
        if (containsAny(text, "budget", "cheap", "cheaper", "save money", "ucuz", "bütçe", "tasarruf", "euro")) {
            return AgentIntent.BUDGET_OPTIMIZE;
        }
        if (containsAny(text, "rain", "weather", "rainy", "yağmur", "hava")) {
            return AgentIntent.RAIN_REPLAN;
        }
        if (containsAny(text, "coffee", "cafe", "food", "dinner", "restaurant", "kahve", "yemek", "akşam")) {
            return AgentIntent.ADD_FOOD_STOP;
        }
        if (containsAny(text, "replace", "swap", "change stop", "değiştir", "yerine")) {
            return AgentIntent.REPLACE_STOP;
        }
        if (containsAny(text, "light", "lighter", "easy", "short", "slow", "less walking", "hafif", "yorul", "kolay", "az yür")) {
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
            case REPLACE_STOP, BUDGET_OPTIMIZE, RAIN_REPLAN -> "replace";
            default -> "lighter";
        };
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

    private ItineraryDay resolveDay(Trip trip, Integer requestedDayNumber) {
        int dayNumber = requestedDayNumber == null ? 1 : requestedDayNumber;
        return itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                .filter(day -> day.getDayNumber() == dayNumber)
                .findFirst()
                .orElseGet(() -> itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Itinerary day was not found")));
    }
}
