package com.journy.backend.destination.repository;

import com.journy.backend.destination.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, String> {
    List<Destination> findTop8ByPopularTrueAndAvailableTrueOrderByNameAsc();

    List<Destination> findTop12ByNameContainingIgnoreCaseOrCountryContainingIgnoreCaseOrderByAvailableDescNameAsc(String name, String country);

    List<Destination> findTop12ByOrderByPopularDescAvailableDescNameAsc();

    Optional<Destination> findByNameIgnoreCase(String name);
}
