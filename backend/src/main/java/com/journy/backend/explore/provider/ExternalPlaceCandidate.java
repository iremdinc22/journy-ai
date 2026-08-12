package com.journy.backend.explore.provider;

import com.journy.backend.place.enums.PlaceCategory;

public record ExternalPlaceCandidate(
        String provider,
        String providerPlaceId,
        String name,
        String city,
        PlaceCategory category,
        String description,
        String priceLevel,
        double rating,
        String imageUrl,
        String address,
        String website,
        double latitude,
        double longitude,
        String openingHours,
        int estimatedVisitMinutes,
        String tags
) {
}
