package com.journy.backend.ai.service;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
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

@Service
public class AiService {
    private final CurrentUserService currentUserService;
    private final TripRepository tripRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiMapper aiMapper;

    public AiService(
            CurrentUserService currentUserService,
            TripRepository tripRepository,
            AiConversationRepository aiConversationRepository,
            AiMapper aiMapper
    ) {
        this.currentUserService = currentUserService;
        this.tripRepository = tripRepository;
        this.aiConversationRepository = aiConversationRepository;
        this.aiMapper = aiMapper;
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

    private record AiDecision(String message, String suggestedAction, Integer minutesSaved) {
    }
}
