package com.journy.backend.profile.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.profile.dto.UpdatePreferencesRequest;
import com.journy.backend.profile.mapper.ProfileMapper;
import com.journy.backend.profile.dto.ProfileResponse;
import com.journy.backend.savedplace.repository.SavedPlaceRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ProfileService {
    private final TripRepository tripRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final ProfileMapper profileMapper;
    private final CurrentUserService currentUserService;
    private static final Set<String> PACES = Set.of("RELAXED", "BALANCED", "FULL");
    private static final Set<String> BUDGETS = Set.of("LEAN", "BALANCED", "COMFORT");
    private static final Set<String> FOOD_MODES = Set.of("LOCAL_FIRST", "BEST_RATED", "BUDGET_FRIENDLY");

    public ProfileService(
            TripRepository tripRepository,
            SavedPlaceRepository savedPlaceRepository,
            UserAccountRepository userAccountRepository,
            ProfileMapper profileMapper,
            CurrentUserService currentUserService
    ) {
        this.tripRepository = tripRepository;
        this.savedPlaceRepository = savedPlaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.profileMapper = profileMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public ProfileResponse me() {
        UserAccount user = currentUserService.currentUser();
        normalizePreferences(user);
        Trip currentTrip = tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
        return profileMapper.toResponse(
                user,
                currentTrip,
                tripRepository.findTop5ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail()),
                savedPlaceRepository.findTop8ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
        );
    }

    @Transactional
    public ProfileResponse updatePreferences(UpdatePreferencesRequest request) {
        UserAccount user = currentUserService.currentUser();
        String pace = normalizeValue(request.defaultPace(), PACES, "default pace");
        String budget = normalizeValue(request.defaultBudget(), BUDGETS, "default budget");
        String foodDiscovery = normalizeValue(request.foodDiscovery(), FOOD_MODES, "food discovery");

        user.setDefaultPace(pace);
        user.setDefaultBudget(budget);
        user.setFoodDiscovery(foodDiscovery);
        user.setPlanChangeNotifications(Boolean.TRUE.equals(request.planChangeNotifications()));
        user.setFoodWindowNotifications(Boolean.TRUE.equals(request.foodWindowNotifications()));
        userAccountRepository.save(user);
        return me();
    }

    private void normalizePreferences(UserAccount user) {
        boolean changed = false;
        if (user.getDefaultPace() == null || user.getDefaultPace().isBlank()) {
            user.setDefaultPace("BALANCED");
            changed = true;
        }
        if (user.getDefaultBudget() == null || user.getDefaultBudget().isBlank()) {
            user.setDefaultBudget("BALANCED");
            changed = true;
        }
        if (user.getFoodDiscovery() == null || user.getFoodDiscovery().isBlank()) {
            user.setFoodDiscovery("LOCAL_FIRST");
            user.setPlanChangeNotifications(true);
            user.setFoodWindowNotifications(true);
            changed = true;
        }
        if (changed) {
            userAccountRepository.save(user);
        }
    }

    private String normalizeValue(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported " + label + ": " + value);
        }
        return normalized;
    }
}
