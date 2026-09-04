package com.journy.backend.support;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import java.time.Instant;

public final class VerifiedPlaceFixtures {
    private VerifiedPlaceFixtures() {}
    public static Place place(String id, String name, String city, PlaceCategory category) {
        var place = new Place(name, city, category, "Verified test fixture", "Mid", 4.5, "");
        place.setId(id); place.setProvider("osm"); place.setProviderPlaceId("node/" + id);
        var coordinates = new com.journy.backend.destination.provider.DestinationCoordinateResolver(java.util.List.of())
                .knownCoordinatesFor(city).orElseThrow();
        place.setProviderFetchedAt(Instant.now());
        place.setLatitude(coordinates.latitude()); place.setLongitude(coordinates.longitude());
        return place;
    }
}
