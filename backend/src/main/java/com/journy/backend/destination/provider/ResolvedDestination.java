package com.journy.backend.destination.provider;

public record ResolvedDestination(
        String originalQuery,
        String displayName,
        String locality,
        String region,
        String country,
        double latitude,
        double longitude,
        String provider,
        String providerPlaceId
) {
}
