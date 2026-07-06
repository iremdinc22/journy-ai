package com.journy.backend.savedplace.mapper;

import com.journy.backend.savedplace.dto.SavedPlaceRequest;
import com.journy.backend.savedplace.dto.SavedPlaceResponse;
import com.journy.backend.savedplace.model.SavedPlace;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Component;

@Component
public class SavedPlaceMapper {
    public SavedPlace toEntity(UserAccount user, SavedPlaceRequest request) {
        return new SavedPlace(
                user,
                request.placeId(),
                request.name(),
                request.city(),
                request.category(),
                request.description(),
                request.priceLevel(),
                request.rating(),
                request.imageUrl(),
                request.address(),
                request.openingHours(),
                request.estimatedVisitMinutes(),
                request.tags()
        );
    }

    public SavedPlaceResponse toResponse(SavedPlace place) {
        return new SavedPlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getName(),
                place.getCity(),
                place.getCategory(),
                place.getDescription(),
                place.getPriceLevel(),
                place.getRating(),
                place.getImageUrl(),
                place.getAddress(),
                place.getOpeningHours(),
                place.getEstimatedVisitMinutes(),
                place.getTags()
        );
    }
}
