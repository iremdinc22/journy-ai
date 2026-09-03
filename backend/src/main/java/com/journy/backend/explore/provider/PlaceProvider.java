package com.journy.backend.explore.provider;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.place.enums.PlaceCategory;

import java.util.List;

public interface PlaceProvider {
    String name();

    List<ExternalPlaceCandidate> search(ResolvedDestination destination, PlaceCategory category, int limit);
}
