package com.journy.backend.savedplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SavedPlaceRequest(
        @NotBlank String placeId,
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String category,
        @NotBlank String description,
        @NotBlank String priceLevel,
        @NotNull Double rating,
        @NotBlank String imageUrl,
        String address,
        String openingHours,
        Integer estimatedVisitMinutes,
        String tags
) {
}
