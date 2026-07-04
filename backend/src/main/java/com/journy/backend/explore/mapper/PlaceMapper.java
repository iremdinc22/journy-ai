package com.journy.backend.explore.mapper;

import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.model.Place;
import org.springframework.stereotype.Component;

@Component
public class PlaceMapper {
    public PlaceResponse toResponse(Place place) {
        return new PlaceResponse(
                place.getId(),
                place.getName(),
                place.getCity(),
                place.getCategory().name(),
                place.getDescription(),
                place.getPriceLevel(),
                place.getRating(),
                place.getImageUrl(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getOpeningHours(),
                place.getEstimatedVisitMinutes(),
                place.getTags()
        );
    }
}
