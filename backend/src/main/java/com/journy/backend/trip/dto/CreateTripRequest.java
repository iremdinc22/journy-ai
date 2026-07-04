package com.journy.backend.trip.dto;

import com.journy.backend.common.validation.ValidTripDates;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;

@ValidTripDates
public record CreateTripRequest(
        @NotBlank String destination,
        String startingArea,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull TravelerType travelerType,
        @NotNull BudgetMode budget,
        @NotNull TripPace pace,
        @NotEmpty Set<TravelInterest> interests
) {
}
