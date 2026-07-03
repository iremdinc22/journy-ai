package com.journy.backend.itinerary.service;

import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.model.Trip;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ItineraryGenerationService {
    private final ItineraryDayRepository itineraryDayRepository;
    private final PlaceRepository placeRepository;

    public ItineraryGenerationService(ItineraryDayRepository itineraryDayRepository, PlaceRepository placeRepository) {
        this.itineraryDayRepository = itineraryDayRepository;
        this.placeRepository = placeRepository;
    }

    public void generateIfMissing(Trip trip) {
        if (!itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId()).isEmpty()) {
            return;
        }

        List<Place> candidatePlaces = selectPlaces(trip);
        int days = trip.dayCount();
        int stopsPerDay = switch (trip.getPace()) {
            case RELAXED -> 3;
            case BALANCED -> 4;
            case FULL -> 5;
        };

        List<ItineraryDay> generatedDays = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            List<Place> dayPlaces = slice(candidatePlaces, (dayNumber - 1) * stopsPerDay, stopsPerDay);
            if (dayPlaces.isEmpty()) {
                dayPlaces = fallbackPlaces(dayNumber);
            }

            double walkKm = calculateWalkKm(dayPlaces.size(), trip.getPace());
            ItineraryDay day = new ItineraryDay(
                    trip,
                    dayNumber,
                    titleFor(dayNumber, dayPlaces),
                    summaryFor(dayPlaces),
                    walkKm
            );

            int order = 1;
            for (Place place : dayPlaces) {
                day.addStop(new ItineraryStop(
                        order,
                        place.getName(),
                        place.getCategory().name(),
                        timeWindowFor(order),
                        place.getDescription(),
                        coordinateFor(dayNumber, order, true),
                        coordinateFor(dayNumber, order, false)
                ));
                order++;
            }
            generatedDays.add(day);
        }

        itineraryDayRepository.saveAll(generatedDays);
        int totalStops = generatedDays.stream().mapToInt(day -> day.getStops().size()).sum();
        int foodPicks = (int) generatedDays.stream()
                .flatMap(day -> day.getStops().stream())
                .filter(stop -> stop.getCategory().equals(PlaceCategory.FOOD.name()) || stop.getCategory().equals(PlaceCategory.COFFEE.name()))
                .count();
        double averageWalk = generatedDays.stream().mapToDouble(ItineraryDay::getWalkKm).average().orElse(0);
        trip.setTotalStops(totalStops);
        trip.setFoodPicks(foodPicks);
        trip.setAverageWalkKm(Math.round(averageWalk * 10.0) / 10.0);
    }

    private List<Place> selectPlaces(Trip trip) {
        Set<PlaceCategory> categories = categoriesFor(trip.getInterests());
        List<Place> places = placeRepository.findAll().stream()
                .filter(place -> place.getCity().equalsIgnoreCase(trip.getDestination()) || place.getCity().equalsIgnoreCase("Amsterdam"))
                .filter(place -> categories.contains(place.getCategory()))
                .filter(place -> budgetAllows(trip.getBudget(), place.getPriceLevel()))
                .sorted(Comparator.comparingDouble(Place::getRating).reversed())
                .toList();

        if (!places.isEmpty()) {
            return places;
        }

        return placeRepository.findTop12ByOrderByRatingDesc().stream()
                .filter(place -> budgetAllows(trip.getBudget(), place.getPriceLevel()))
                .toList();
    }

    private Set<PlaceCategory> categoriesFor(List<TravelInterest> interests) {
        Set<PlaceCategory> categories = EnumSet.of(PlaceCategory.FOOD, PlaceCategory.CULTURE, PlaceCategory.COFFEE, PlaceCategory.WALKING);
        if (interests.contains(TravelInterest.COFFEE)) categories.add(PlaceCategory.COFFEE);
        if (interests.contains(TravelInterest.MUSEUMS) || interests.contains(TravelInterest.CULTURE)) categories.add(PlaceCategory.CULTURE);
        if (interests.contains(TravelInterest.LOCAL_FOOD)) categories.add(PlaceCategory.FOOD);
        if (interests.contains(TravelInterest.FREE_ACTIVITIES)) categories.add(PlaceCategory.FREE);
        if (interests.contains(TravelInterest.WALKING)) categories.add(PlaceCategory.WALKING);
        return categories;
    }

    private boolean budgetAllows(BudgetMode budget, String priceLevel) {
        String normalized = priceLevel == null ? "" : priceLevel.toLowerCase();
        return switch (budget) {
            case LEAN -> normalized.equals("free") || normalized.equals("lean");
            case BALANCED -> !normalized.equals("comfort");
            case COMFORT -> true;
        };
    }

    private List<Place> slice(List<Place> places, int start, int count) {
        if (start >= places.size()) {
            return List.of();
        }
        return places.subList(start, Math.min(start + count, places.size()));
    }

    private List<Place> fallbackPlaces(int dayNumber) {
        return List.of(
                new Place("Neighborhood anchor", "Generated", PlaceCategory.CULTURE, "A flexible culture stop for day " + dayNumber + ".", "Free", 4.4, ""),
                new Place("Local coffee break", "Generated", PlaceCategory.COFFEE, "A calm pause to keep the day realistic.", "Lean", 4.5, ""),
                new Place("Dinner area", "Generated", PlaceCategory.FOOD, "Finish near the last stop instead of crossing the city.", "Mid", 4.6, "")
        );
    }

    private String titleFor(int dayNumber, List<Place> places) {
        boolean hasFood = places.stream().anyMatch(place -> place.getCategory() == PlaceCategory.FOOD);
        boolean hasCulture = places.stream().anyMatch(place -> place.getCategory() == PlaceCategory.CULTURE);
        if (hasFood && hasCulture) return "Culture & local food";
        if (hasCulture) return "Culture-led city day";
        if (hasFood) return "Local food route";
        return "Balanced city day " + dayNumber;
    }

    private String summaryFor(List<Place> places) {
        int stopCount = places.size();
        return "A realistic day with " + stopCount + " nearby stops, local breaks and enough room to adjust the pace.";
    }

    private String timeWindowFor(int order) {
        return switch (order) {
            case 1 -> "09:30";
            case 2 -> "11:30";
            case 3 -> "14:00";
            case 4 -> "17:00";
            default -> "19:00";
        };
    }

    private double calculateWalkKm(int stopCount, com.journy.backend.trip.enums.TripPace pace) {
        double base = switch (pace) {
            case RELAXED -> 1.1;
            case BALANCED -> 1.35;
            case FULL -> 1.55;
        };
        return Math.round(stopCount * base * 10.0) / 10.0;
    }

    private double coordinateFor(int dayNumber, int order, boolean latitude) {
        double base = latitude ? 52.3676 : 4.9041;
        double delta = (dayNumber * 0.004) + (order * 0.002);
        return latitude ? base - delta : base + delta;
    }
}
