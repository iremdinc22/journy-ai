package com.journy.backend.savedplace.dto;

public record SavedPlaceResponse(
        String id,
        String placeId,
        String name,
        String city,
        String category,
        String description,
        String priceLevel,
        double rating,
        String imageUrl,
        String address,
        String openingHours,
        Integer estimatedVisitMinutes,
        String tags
) {
}
