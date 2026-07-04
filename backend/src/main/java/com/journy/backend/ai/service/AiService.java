package com.journy.backend.ai.service;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.dto.AiItinerarySuggestionRequest;
import com.journy.backend.ai.dto.AiItinerarySuggestionResponse;
import com.journy.backend.common.exception.ResourceNotFoundException;
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
    private final AiConversationRepository aiConversationRepository;
    private final AiMapper aiMapper;

    public AiService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            AiConversationRepository aiConversationRepository,
            AiMapper aiMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
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
    public AiChatResponse chat(AiChatRequest request) {
        UserAccount user = currentUserService.currentUser();
        Trip trip = resolveTrip(user, request.tripId()).orElse(null);
        AiConversation conversation = resolveConversation(user, trip);
        conversation.addMessage(new AiMessage("USER", request.message()));

        AiDecision decision = decide(request.message());
        conversation.addMessage(new AiMessage("ASSISTANT", decision.message()));
        AiConversation savedConversation = aiConversationRepository.save(conversation);

        return aiMapper.toChatResponse(
                savedConversation,
                decision.message(),
                decision.suggestedAction(),
                decision.minutesSaved()
        );
    }

    private AiDecision decide(String message) {
        String text = message.toLowerCase();
        if (text.contains("coffee") || text.contains("cafe")) {
            return new AiDecision(
                    "I found a quiet coffee break that fits between your museum stop and canal walk.",
                    "Add coffee stop",
                    0
            );
        }
        if (text.contains("rain") || text.contains("weather")) {
            return new AiDecision(
                    "I would move the outdoor canal walk to a clearer window and keep today focused on indoor stops.",
                    "Rebuild around rain",
                    18
            );
        }
        if (text.contains("dinner") || text.contains("food")) {
            return new AiDecision(
                    "Stay near the final neighborhood for dinner. It avoids a long transfer after the busiest part of the day.",
                    "Suggest dinner area",
                    12
            );
        }
        if (text.contains("light") || text.contains("easy") || text.contains("short")) {
            return new AiDecision(
                    "Remove one optional stop and add a longer break after lunch. You keep the main experience but the day feels lighter.",
                    "Lighten day",
                    22
            );
        }

        return new AiDecision(
                "I can help with that. I would keep the main anchor stops, reduce backtracking and leave one flexible window.",
                "Adjust route",
                null
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

    private record AiDecision(String message, String suggestedAction, Integer minutesSaved) {
    }
}
