package com.journy.backend.itinerary.repository;

import com.journy.backend.itinerary.model.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, String> {
    List<ItineraryDay> findByTripIdOrderByDayNumberAsc(String tripId);

    void deleteByTripId(String tripId);
}
