package com.journy.backend.startarea;

public record StartAreaSuggestion(
        String id, String name, String type, Double latitude, Double longitude,
        String source, String providerPlaceId
) {}
