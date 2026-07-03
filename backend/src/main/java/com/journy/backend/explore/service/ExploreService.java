package com.journy.backend.explore.service;

import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.mapper.PlaceMapper;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ExploreService {
    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;

    public ExploreService(PlaceRepository placeRepository, PlaceMapper placeMapper) {
        this.placeRepository = placeRepository;
        this.placeMapper = placeMapper;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> places(String category) {
        List<Place> places = category == null || category.isBlank() || category.equalsIgnoreCase("For you")
                ? placeRepository.findTop12ByOrderByRatingDesc()
                : placeRepository.findByCategoryOrderByRatingDesc(parseCategory(category));
        return places.stream().map(placeMapper::toResponse).toList();
    }

    private PlaceCategory parseCategory(String category) {
        try {
            return PlaceCategory.valueOf(category.trim().toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported place category: " + category);
        }
    }
}
