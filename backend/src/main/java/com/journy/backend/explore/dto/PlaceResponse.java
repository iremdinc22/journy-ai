package com.journy.backend.explore.dto;

public record PlaceResponse(
        String id,
        String name,
        String city,
        String category,
        String description,
        String priceLevel,
        double rating,
        String imageUrl,
        String address,
        Double latitude,
        Double longitude,
        String openingHours,
        Integer estimatedVisitMinutes,
        String tags
) {
}
