package com.journy.backend.feedback.service;

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

    public TasteFeedbackService(TasteFeedbackRepository tasteFeedbackRepository, CurrentUserService currentUserService) {
        this.tasteFeedbackRepository = tasteFeedbackRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public TasteFeedbackResponse record(TasteFeedbackRequest request) {
        UserAccount user = currentUserService.currentUser();
        return toResponse(record(user, request.placeId(), request.placeName(), request.category(), request.action(), request.reason()));
    }

    @Transactional
    public TasteFeedback record(UserAccount user, String placeId, String placeName, String category, TasteFeedbackAction action, String reason) {
        TasteFeedback feedback = new TasteFeedback(
                user,
                placeId,
                fallback(placeName, "Place"),
                fallback(category, "WALKING"),
                action,
                weightFor(action),
                reason
        );
        return tasteFeedbackRepository.save(feedback);
    }

    public int weightFor(TasteFeedbackAction action) {
        return switch (action) {
            case SAVED -> 3;
            case VISITED, ALREADY_VISITED -> 4;
            case REMOVED, SKIPPED, REPLACED -> -2;
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

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
