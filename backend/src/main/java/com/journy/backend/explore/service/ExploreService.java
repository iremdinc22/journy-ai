package com.journy.backend.explore.service;

import com.journy.backend.explore.dto.DestinationResponse;
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

    @Transactional(readOnly = true)
    public List<DestinationResponse> destinations() {
        return placeRepository.findDistinctCities().stream()
                .map(city -> new DestinationResponse(
                        city,
                        imageFor(city),
                        metaFor(city),
                        (int) placeRepository.countByCityIgnoreCase(city)
                ))
                .toList();
    }

    private PlaceCategory parseCategory(String category) {
        try {
            return PlaceCategory.valueOf(category.trim().toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported place category: " + category);
        }
    }

    private String metaFor(String city) {
        return switch (city.toLowerCase()) {
            case "paris" -> "Museums - bakeries - walks";
            case "rome" -> "History - piazzas - dinner";
            case "barcelona" -> "Design - beach - tapas";
            case "amsterdam" -> "Canals - coffee - museums";
            default -> "Local picks - culture - food";
        };
    }

    private String imageFor(String city) {
        return switch (city.toLowerCase()) {
            case "paris" -> "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=88";
            case "rome" -> "https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=88";
            case "barcelona" -> "https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=88";
            case "amsterdam" -> "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=88";
            default -> "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=88";
        };
    }
}
