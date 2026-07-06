package com.journy.backend.savedplace.repository;

import com.journy.backend.savedplace.model.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, String> {
    List<SavedPlace> findTop8ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<SavedPlace> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<SavedPlace> findByUserEmailIgnoreCaseAndPlaceId(String email, String placeId);

    boolean existsByUserEmailIgnoreCaseAndPlaceId(String email, String placeId);
}
