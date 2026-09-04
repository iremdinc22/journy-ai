package com.journy.backend.explore.search;

import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.mapper.PlaceMapper;
import com.journy.backend.explore.provider.PlaceProviderService;
import com.journy.backend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Comparator;
import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class PlaceSearchService {
    private final CurrentUserService users;
    private final DestinationResolutionService destinations;
    private final PlaceProviderService providers;
    private final PlaceMapper mapper;
    public PlaceSearchService(CurrentUserService users, DestinationResolutionService destinations,
            PlaceProviderService providers, PlaceMapper mapper) {
        this.users = users; this.destinations = destinations; this.providers = providers; this.mapper = mapper;
    }
    public List<PlaceResponse> search(String city, String query) {
        users.currentUser();
        if (city == null || city.isBlank() || city.length() > 140 || query == null
                || query.trim().length() < 2 || query.length() > 100
                || query.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(BAD_REQUEST, "Provide a destination and a search term of 2–100 characters");
        }
        var search = PlaceSearchQuery.of(query);
        var destination = destinations.resolve(city.trim()).orElseThrow(() ->
                new ResponseStatusException(BAD_REQUEST, "Destination could not be resolved"));
        // Search relevance only; the existing Explore personalization formula is untouched.
        return providers.searchPlaces(destination, search, 40).stream()
                .sorted(Comparator.comparingInt((com.journy.backend.explore.model.Place p) ->
                        PlaceSearchQuery.normalize(p.getName()).equals(search.text()) ? 0
                                : PlaceSearchQuery.normalize(p.getName()).startsWith(search.text()) ? 1 : 2)
                        .thenComparing(p -> PlaceSearchQuery.normalize(p.getName()))
                        .thenComparing(com.journy.backend.explore.model.Place::getId))
                .limit(20).map(mapper::toResponse).toList();
    }
}
