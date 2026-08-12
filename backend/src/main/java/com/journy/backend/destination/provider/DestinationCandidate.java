package com.journy.backend.destination.provider;

public record DestinationCandidate(
        String provider,
        String providerPlaceId,
        String name,
        String country,
        String displayName,
        double latitude,
        double longitude
) {
}
