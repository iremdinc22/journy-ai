package com.journy.backend.explore.provider;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.place.enums.PlaceCategory;

import java.util.List;

public interface PlaceProvider {
    default List<com.journy.backend.startarea.StartAreaSuggestion> searchStartAreas(ResolvedDestination destination, String query, int limit) {
        return List.of();
    }

    String name();

    List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit);
}
