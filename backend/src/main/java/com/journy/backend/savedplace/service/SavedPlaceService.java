package com.journy.backend.savedplace.service;

import com.journy.backend.savedplace.dto.SavedPlaceRequest;
import com.journy.backend.savedplace.dto.SavedPlaceResponse;
import com.journy.backend.savedplace.dto.SavedPlaceStatusResponse;
import com.journy.backend.savedplace.mapper.SavedPlaceMapper;
import com.journy.backend.savedplace.model.SavedPlace;
import com.journy.backend.savedplace.repository.SavedPlaceRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SavedPlaceService {
    private final CurrentUserService currentUserService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final SavedPlaceMapper savedPlaceMapper;

    public SavedPlaceService(
            CurrentUserService currentUserService,
            SavedPlaceRepository savedPlaceRepository,
            SavedPlaceMapper savedPlaceMapper
    ) {
        this.currentUserService = currentUserService;
        this.savedPlaceRepository = savedPlaceRepository;
        this.savedPlaceMapper = savedPlaceMapper;
    }

    @Transactional(readOnly = true)
    public List<SavedPlaceResponse> list() {
        UserAccount user = currentUserService.currentUser();
        return savedPlaceRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail()).stream()
                .map(savedPlaceMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedPlaceStatusResponse status(String placeId) {
        UserAccount user = currentUserService.currentUser();
        return new SavedPlaceStatusResponse(savedPlaceRepository.existsByUserEmailIgnoreCaseAndPlaceId(user.getEmail(), placeId));
    }

    @Transactional
    public SavedPlaceResponse save(SavedPlaceRequest request) {
        UserAccount user = currentUserService.currentUser();
        SavedPlace place = savedPlaceRepository.findByUserEmailIgnoreCaseAndPlaceId(user.getEmail(), request.placeId())
                .orElseGet(() -> savedPlaceRepository.save(savedPlaceMapper.toEntity(user, request)));
        return savedPlaceMapper.toResponse(place);
    }

    @Transactional
    public void remove(String placeId) {
        UserAccount user = currentUserService.currentUser();
        savedPlaceRepository.findByUserEmailIgnoreCaseAndPlaceId(user.getEmail(), placeId)
                .ifPresent(savedPlaceRepository::delete);
    }
}
