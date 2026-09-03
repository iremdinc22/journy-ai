package com.journy.backend.itinerary.service;

import com.journy.backend.itinerary.model.ItineraryStop;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whole localized titles, never translations of fragments or inferred POI names. */
public final class DayTitleGenerator {
    private DayTitleGenerator() {}

    private static final Map<String, String> EN = Map.of(
            "CULTURE", "Culture", "COFFEE", "Coffee", "FOOD", "Local Food", "WALKING", "Walks", "FREE", "Open Air");
    private static final Map<String, String> TR = Map.of(
            "CULTURE", "Kültür", "COFFEE", "Kahve", "FOOD", "Yerel Lezzetler", "WALKING", "Yürüyüş", "FREE", "Açık Hava");

    public static boolean hasVerifiedStops(List<ItineraryStop> stops) {
        return stops.stream().anyMatch(DayTitleGenerator::providerBacked);
    }

    public static Map<String, String> translations(List<ItineraryStop> stops) {
        return Map.of("en", title(stops, "en"), "tr", title(stops, "tr"));
    }

    public static String title(List<ItineraryStop> stops, String language) {
        boolean turkish = "tr".equals(language);
        Map<String, String> labels = turkish ? TR : EN;
        List<ItineraryStop> real = stops.stream().filter(DayTitleGenerator::providerBacked)
                .sorted(Comparator.comparingInt(ItineraryStop::getStopOrder)).toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var stop : real) {
            if (stop.getCategory() != null && labels.containsKey(stop.getCategory())) {
                counts.merge(stop.getCategory(), 1, Integer::sum);
            }
        }
        // Stable ties follow the actual visit order, not interests or destination templates.
        List<String> themes = counts.keySet().stream()
                .sorted(Comparator.comparingInt((String category) -> counts.get(category)).reversed())
                .limit(2).toList();
        if (themes.isEmpty()) return turkish ? "Şehir Keşfi" : "City Discovery";
        String theme = String.join(turkish ? " ve " : " & ", themes.stream().map(labels::get).toList());
        String anchor = real.stream().filter(stop -> themes.getFirst().equals(stop.getCategory()))
                .map(ItineraryStop::getTitle).filter(name -> name != null && !name.isBlank())
                .findFirst().orElse("");
        String anchored = anchor + ": " + theme;
        if (!anchor.isBlank() && anchor.length() <= 24 && anchored.length() <= 52
                && anchored.split("\\s+").length <= 7) {
            return anchored;
        }
        return theme + (turkish ? " Rotası" : " Route");
    }

    private static boolean providerBacked(ItineraryStop stop) {
        return stop != null && stop.getPlaceId() != null && !stop.getPlaceId().isBlank()
                && stop.getSource() != null && stop.getSource().startsWith("provider:")
                && stop.getSource().length() > "provider:".length();
    }
}
