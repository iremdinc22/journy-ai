package com.journy.backend.trip.dto;

import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TripPace;

import java.time.LocalDate;
import java.util.Set;

public record TripPreviewRequest(
        String destination,
        String startingArea,
        LocalDate startDate,
        LocalDate endDate,
        BudgetMode budget,
        TripPace pace,
        Set<TravelInterest> interests
) {
}
