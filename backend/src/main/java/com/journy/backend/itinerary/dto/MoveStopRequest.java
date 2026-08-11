package com.journy.backend.itinerary.dto;

import jakarta.validation.constraints.Min;

public record MoveStopRequest(
        @Min(1)
        int targetDayNumber
) {
}
