package com.journy.backend.feedback.service;

import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.explore.model.Place;
import com.journy.backend.itinerary.service.PlannerPlaceContract;
import com.journy.backend.common.exception.ResourceNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import java.util.Set;
import java.util.Optional;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

import com.journy.backend.feedback.dto.TasteFeedbackRequest;
import com.journy.backend.feedback.dto.TasteFeedbackResponse;
import com.journy.backend.feedback.model.TasteFeedback;
import com.journy.backend.feedback.model.TasteFeedbackAction;
import com.journy.backend.feedback.repository.TasteFeedbackRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TasteFeedbackService {
    private final TasteFeedbackRepository tasteFeedbackRepository;
    private final CurrentUserService currentUserService;

    private final PlaceRepository places;

    public TasteFeedbackService(TasteFeedbackRepository tasteFeedbackRepository, CurrentUserService currentUserService,
            PlaceRepository places) {
        this.tasteFeedbackRepository = tasteFeedbackRepository;
        this.currentUserService = currentUserService;
        this.places = places;
    }

    @Transactional
    public TasteFeedbackResponse record(TasteFeedbackRequest request) {
        UserAccount user = currentUserService.currentUserForUpdate();
        if (request.action() == null || !Set.of(TasteFeedbackAction.NOT_INTERESTED,
                TasteFeedbackAction.TOO_EXPENSIVE, TasteFeedbackAction.TOO_FAR,
                TasteFeedbackAction.ALREADY_VISITED).contains(request.action())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Action requires a committed state transition");
        }
        var place = verifiedPlace(request.placeId()).orElseThrow(() ->
                new ResourceNotFoundException("Verified place was not found"));
        return toResponse(persist(user, place, request.action(), request.reason(), "EXPLORE", place.getId()));
    }

    // Internal state owners call this in their transaction. Historical snapshots remain operable.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransition(String placeId, TasteFeedbackAction action, String reason, String source, String contextId) {
        UserAccount user = currentUserService.currentUserForUpdate();
        verifiedPlace(placeId).ifPresent(place -> persist(user, place, action, reason, source, contextId));
    }

    private Optional<Place> verifiedPlace(String id) {
        return id == null || id.isBlank() ? Optional.empty()
                : places.findById(id).filter(PlannerPlaceContract::verified);
    }

    private TasteFeedback persist(UserAccount user, Place place,
            TasteFeedbackAction action, String reason, String source, String contextId) {
        // Length-prefixed components avoid delimiter collisions; SHA-256 bounds the index key.
        String raw = source.length() + ":" + source + contextId.length() + ":" + contextId + ":" + action.name();
        String key;
        try {
            key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return tasteFeedbackRepository.findByUserIdAndEventKey(user.getId(), key).orElseGet(() -> {
            TasteFeedback feedback = new TasteFeedback(user, place.getId(), place.getName(),
                    place.getCategory().name(), action, weightFor(action), reason);
            feedback.setIdentityVerified(true);
            feedback.setSource(source);
            feedback.setContextId(contextId);
            feedback.setEventKey(key);
            return tasteFeedbackRepository.save(feedback);
        });
    }

    public int weightFor(TasteFeedbackAction action) {
        return switch (action) {
            case SAVED -> 3;
            case ADDED_TO_TRIP -> 0; // New observation; scoring is deferred to the Taste Profile phase.
            case VISITED, ALREADY_VISITED -> 4;
            case REMOVED, UNSAVED, REMOVED_FROM_TRIP, SKIPPED, REPLACED -> -2;
            case NOT_INTERESTED -> -3;
            case TOO_EXPENSIVE, TOO_FAR -> -2;
        };
    }

    private TasteFeedbackResponse toResponse(TasteFeedback feedback) {
        return new TasteFeedbackResponse(
                feedback.getId(),
                feedback.getPlaceId(),
                feedback.getPlaceName(),
                feedback.getCategory(),
                feedback.getAction().name(),
                feedback.getWeight(),
                feedback.getReason()
        );
    }

}
