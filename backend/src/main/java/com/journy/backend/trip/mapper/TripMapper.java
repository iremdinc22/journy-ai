package com.journy.backend.trip.mapper;

import com.journy.backend.trip.dto.TripResponse;
import com.journy.backend.trip.model.Trip;
import org.springframework.stereotype.Component;

@Component
public class TripMapper {
    public TripResponse toResponse(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.dayCount(),
                trip.getTravelerType().name(),
                trip.getBudget().name(),
                trip.getPace().name(),
                trip.getInterests().stream().map(Enum::name).toList(),
                new TripResponse.TripStats(
                        trip.getTotalStops(),
                        trip.getFoodPicks(),
                        trip.getAverageWalkKm()
                )
        );
    }
}
