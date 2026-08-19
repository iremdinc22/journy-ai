package com.journy.backend.itinerary.dto;

import com.journy.backend.itinerary.model.StopVisitStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStopStatusRequest(
        @NotNull StopVisitStatus status
) {
}
