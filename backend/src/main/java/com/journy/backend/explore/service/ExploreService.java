package com.journy.backend.explore.service;

import com.journy.backend.destination.provider.DestinationImageResolver;
import com.journy.backend.destination.provider.DestinationCoordinateResolver;
import com.journy.backend.explore.dto.DestinationResponse;
import com.journy.backend.explore.dto.PlaceResponse;
import com.journy.backend.explore.mapper.PlaceMapper;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.provider.PlaceProviderService;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ExploreService {
    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;
    private final PlaceProviderService placeProviderService;
    private final DestinationImageResolver destinationImageResolver;
    private final DestinationCoordinateResolver destinationCoordinateResolver;

    public ExploreService(
            PlaceRepository placeRepository,
            PlaceMapper placeMapper,
            PlaceProviderService placeProviderService,
            DestinationImageResolver destinationImageResolver,
            DestinationCoordinateResolver destinationCoordinateResolver
    ) {
        this.placeRepository = placeRepository;
        this.placeMapper = placeMapper;
        this.placeProviderService = placeProviderService;
        this.destinationImageResolver = destinationImageResolver;
        this.destinationCoordinateResolver = destinationCoordinateResolver;
    }

    @Transactional
    public List<PlaceResponse> places(String category, String city) {
        String normalizedCity = city == null ? "" : city.trim();
        boolean hasCity = !normalizedCity.isBlank();
        boolean forYou = category == null || category.isBlank() || category.equalsIgnoreCase("For you");
        PlaceCategory parsedCategory = forYou ? null : parseCategory(category);

        List<Place> places;
        if (hasCity) {
            places = loadCityPlaces(normalizedCity, parsedCategory, forYou);
        } else if (forYou) {
            places = placeRepository.findTop12ByOrderByRatingDesc();
        } else {
            places = placeRepository.findByCategoryOrderByRatingDesc(parsedCategory);
        }

        List<PlaceResponse> responses = places.stream().map(placeMapper::toResponse).toList();
        if (hasCity && responses.size() < 4) {
            return starterPicks(normalizedCity, parsedCategory, responses);
        }
        return responses;
    }

    private List<Place> loadCityPlaces(String city, PlaceCategory category, boolean forYou) {
        List<Place> cached = forYou
                ? placeRepository.findByCityIgnoreCaseOrderByRatingDesc(city)
                : placeRepository.findByCityIgnoreCaseAndCategoryOrderByRatingDesc(city, category);
        int minimum = forYou ? 10 : 4;
        if (cached.size() >= minimum) {
            return cached;
        }
        placeProviderService.enrichCity(city, category, 18);
        return forYou
                ? placeRepository.findByCityIgnoreCaseOrderByRatingDesc(city)
                : placeRepository.findByCityIgnoreCaseAndCategoryOrderByRatingDesc(city, category);
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
            case "london" -> "Markets - parks - museums";
            case "lisbon" -> "Views - cafes - seafood";
            case "prague" -> "Old Town - river - cafes";
            case "vienna" -> "Museums - cafes - markets";
            case "milan", "milano" -> "Design - aperitivo - cathedral walks";
            case "munich", "münchen" -> "Museums - gardens - beer halls";
            case "brussels", "bruxelles", "brussel" -> "Chocolate - galleries - grand squares";
            case "budapest" -> "Thermal baths - river walks - cafes";
            case "edirne" -> "Ottoman heritage - river walks - local food";
            case "edinburgh", "edinburg" -> "Castle views - old town - pubs";
            default -> "Local picks - culture - food";
        };
    }

    private String imageFor(String city) {
        return switch (city.toLowerCase()) {
            case "paris" -> "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=88";
            case "rome" -> "https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=88";
            case "barcelona" -> "https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=88";
            case "amsterdam" -> "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=88";
            case "london" -> "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=900&q=88";
            case "lisbon" -> "https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=900&q=88";
            case "prague" -> "https://images.unsplash.com/photo-1519677100203-a0e668c92439?auto=format&fit=crop&w=900&q=88";
            case "vienna" -> "https://images.unsplash.com/photo-1516550893923-42d28e5677af?auto=format&fit=crop&w=900&q=88";
            case "milan", "milano" -> "https://images.unsplash.com/photo-1567760855784-589f09ed5dc6?auto=format&fit=crop&w=900&q=88";
            case "munich", "münchen" -> "https://images.unsplash.com/photo-1595867818082-083862f3d630?auto=format&fit=crop&w=900&q=88";
            case "brussels", "bruxelles", "brussel" -> "https://images.unsplash.com/photo-1491557345352-5929e343eb89?auto=format&fit=crop&w=900&q=88";
            case "budapest" -> "https://images.unsplash.com/photo-1549877452-9c387954fbc2?auto=format&fit=crop&w=900&q=88";
            case "edirne" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Selimiye_Mosque_Edirne.jpg?width=900";
            case "edinburgh", "edinburg" -> "https://commons.wikimedia.org/wiki/Special:FilePath/Edinburgh_Castle_31_July_2011.jpg?width=900";
            default -> destinationImageResolver.imageFor(city, "Provider-backed");
        };
    }

    private List<PlaceResponse> starterPicks(String city, PlaceCategory category, List<PlaceResponse> existing) {
        java.util.ArrayList<PlaceResponse> picks = new java.util.ArrayList<>(existing);
        List<PlaceCategory> categories = category == null
                ? List.of(PlaceCategory.WALKING, PlaceCategory.COFFEE, PlaceCategory.FOOD, PlaceCategory.CULTURE, PlaceCategory.FREE)
                : List.of(category);

        int index = 1;
        for (PlaceCategory starterCategory : categories) {
            while (picks.stream().filter(place -> place.category().equals(starterCategory.name())).count() < (category == null ? 1 : 4)
                    && picks.size() < 8) {
                picks.add(starterPick(city, starterCategory, index));
                index++;
            }
        }
        return picks;
    }

    private PlaceResponse starterPick(String city, PlaceCategory category, int index) {
        String title = city + " " + starterTitle(category, index);
        return new PlaceResponse(
                "starter_" + slug(city) + "_" + category.name().toLowerCase(Locale.ROOT) + "_" + index,
                title,
                city,
                category.name(),
                starterDescription(city, category),
                category == PlaceCategory.FREE || category == PlaceCategory.WALKING ? "Free" : "Mid",
                Math.round((4.55 + (index % 4) * 0.07) * 10.0) / 10.0,
                starterImage(city, category),
                city + " city center",
                "starter",
                null,
                null,
                fallbackLatitude(city, index),
                fallbackLongitude(city, index),
                starterHours(category),
                starterDuration(category),
                category.name().toLowerCase(Locale.ROOT) + ",starter,current-trip"
        );
    }

    private String starterTitle(PlaceCategory category, int index) {
        return switch (category) {
            case WALKING -> "Neighborhood walk " + index;
            case COFFEE -> "Coffee pause " + index;
            case FOOD -> "Local food stop " + index;
            case CULTURE -> "Culture window " + index;
            case FREE -> "Free city moment " + index;
        };
    }

    private String starterDescription(String city, PlaceCategory category) {
        return switch (category) {
            case WALKING -> "A flexible " + city + " walking pick shaped for the current route.";
            case COFFEE -> "A coffee break candidate that keeps the " + city + " day from feeling rushed.";
            case FOOD -> "A local food window for your " + city + " plan, ready to add into a day.";
            case CULTURE -> "A culture anchor candidate for a more distinctive " + city + " itinerary.";
            case FREE -> "A low-cost " + city + " moment that keeps the day useful without stretching budget.";
        };
    }

    private String starterImage(String city, PlaceCategory category) {
        String cityImage = imageFor(city);
        if (category == PlaceCategory.COFFEE) {
            return "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85";
        }
        if (category == PlaceCategory.FOOD) {
            return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=700&q=85";
        }
        return cityImage;
    }

    private String starterHours(PlaceCategory category) {
        return switch (category) {
            case COFFEE -> "08:00 - 18:00";
            case FOOD -> "12:00 - 22:30";
            case CULTURE -> "10:00 - 18:00";
            default -> "Flexible route window";
        };
    }

    private int starterDuration(PlaceCategory category) {
        return switch (category) {
            case FOOD -> 90;
            case CULTURE -> 120;
            case COFFEE -> 45;
            default -> 60;
        };
    }

    private double fallbackLatitude(String city, int index) {
        return destinationCoordinateResolver.latitudeFor(city, index);
    }

    private double fallbackLongitude(String city, int index) {
        return destinationCoordinateResolver.longitudeFor(city, index);
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
