package com.journy.backend.destination.dto;

public record DestinationResponse(
        String id,
        String name,
        String country,
        String description,
        String imageUrl,
        String tags,
        String bestFor,
        int placeCount,
        double averageDailyWalkKm,
        boolean available,
        boolean popular
) {
}
