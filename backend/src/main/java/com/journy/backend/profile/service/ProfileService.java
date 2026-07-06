package com.journy.backend.profile.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.profile.mapper.ProfileMapper;
import com.journy.backend.profile.dto.ProfileResponse;
import com.journy.backend.savedplace.repository.SavedPlaceRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final TripRepository tripRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final ProfileMapper profileMapper;
    private final CurrentUserService currentUserService;

    public ProfileService(
            TripRepository tripRepository,
            SavedPlaceRepository savedPlaceRepository,
            ProfileMapper profileMapper,
            CurrentUserService currentUserService
    ) {
        this.tripRepository = tripRepository;
        this.savedPlaceRepository = savedPlaceRepository;
        this.profileMapper = profileMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me() {
        UserAccount user = currentUserService.currentUser();
        Trip currentTrip = tripRepository.findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Current trip was not found"));
        return profileMapper.toResponse(
                user,
                currentTrip,
                tripRepository.findTop5ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail()),
                savedPlaceRepository.findTop8ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
        );
    }
}
