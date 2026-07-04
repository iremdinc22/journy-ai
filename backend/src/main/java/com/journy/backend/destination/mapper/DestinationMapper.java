package com.journy.backend.destination.mapper;

import com.journy.backend.destination.dto.DestinationResponse;
import com.journy.backend.destination.model.Destination;
import org.springframework.stereotype.Component;

@Component
public class DestinationMapper {
    public DestinationResponse toResponse(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getCountry(),
                destination.getDescription(),
                destination.getImageUrl(),
                destination.getTags(),
                destination.getBestFor(),
                destination.getPlaceCount(),
                destination.getAverageDailyWalkKm(),
                destination.isAvailable(),
                destination.isPopular()
        );
    }
}
