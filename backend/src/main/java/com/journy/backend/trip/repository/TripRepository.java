package com.journy.backend.trip.repository;

import com.journy.backend.trip.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, String> {
    Optional<Trip> findFirstByCurrentTripTrueOrderByCreatedAtDesc();

    Optional<Trip> findFirstByUserEmailIgnoreCaseAndCurrentTripTrueOrderByCreatedAtDesc(String email);

    List<Trip> findTop5ByOrderByCreatedAtDesc();

    List<Trip> findTop5ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<Trip> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
