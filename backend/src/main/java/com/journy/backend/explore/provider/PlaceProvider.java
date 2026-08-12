package com.journy.backend.explore.provider;

import com.journy.backend.place.enums.PlaceCategory;

import java.util.List;

public interface PlaceProvider {
    String name();

    List<ExternalPlaceCandidate> search(String city, PlaceCategory category, int limit);
}
