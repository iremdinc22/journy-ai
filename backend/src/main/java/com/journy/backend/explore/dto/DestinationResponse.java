package com.journy.backend.explore.dto;

public record DestinationResponse(
        String name,
        String imageUrl,
        String meta,
        int placeCount
) {
}
