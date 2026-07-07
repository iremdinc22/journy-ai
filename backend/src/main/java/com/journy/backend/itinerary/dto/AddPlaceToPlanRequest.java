package com.journy.backend.itinerary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddPlaceToPlanRequest(
        @NotBlank String placeId,
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String category,
        @NotBlank String description,
        @NotBlank String priceLevel,
        @NotNull Double rating,
        String address,
        Double latitude,
        Double longitude,
        String openingHours,
        Integer estimatedVisitMinutes,
        String tags
) {
}
