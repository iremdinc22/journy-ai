package com.journy.backend.explore.search;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import java.text.Normalizer;
import java.util.Locale;

/** Search aliases only; existing profile/category mappings are unchanged. */
public record PlaceSearchQuery(String text, PlaceCategory category, String type) {
    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ı', 'i').replaceAll("\\s+", " ");
    }
    public static PlaceSearchQuery of(String query) {
        String text = normalize(query);
        return switch (text) {
            case "coffee", "cafe", "cafes", "kahve", "kafe" -> new PlaceSearchQuery(text, PlaceCategory.COFFEE, "");
            case "museum", "museums", "muze" -> new PlaceSearchQuery(text, PlaceCategory.CULTURE, "museum");
            case "gallery", "galleries", "galeri" -> new PlaceSearchQuery(text, PlaceCategory.CULTURE, "gallery");
            case "culture", "kultur" -> new PlaceSearchQuery(text, PlaceCategory.CULTURE, "");
            case "food", "restaurant", "restaurants", "yemek", "restoran" -> new PlaceSearchQuery(text, PlaceCategory.FOOD, "");
            case "walking", "walk", "yuruyus" -> new PlaceSearchQuery(text, PlaceCategory.WALKING, "");
            case "park", "parks" -> new PlaceSearchQuery(text, PlaceCategory.WALKING, "park");
            default -> new PlaceSearchQuery(text, null, "");
        };
    }
    public boolean matches(Place place) {
        if (category == null) return normalize(place.getName()).contains(text);
        if (place.getCategory() != category) return false;
        return type.isEmpty() || normalize(place.getName()).contains(text)
                || java.util.Arrays.asList(normalize(place.getTags()).split(",")).contains("search-type:" + type);
    }
}
