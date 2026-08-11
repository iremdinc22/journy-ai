package com.journy.backend.itinerary.dto;

import jakarta.validation.constraints.Min;

public record ReorderStopRequest(
        @Min(1)
        int targetOrder
) {
}
