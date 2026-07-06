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
import java.util.HashSet;
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
        Set<String> usedPlaceIds = new HashSet<>();
        int days = trip.dayCount();
        int stopsPerDay = stopsPerDay(trip);

        List<ItineraryDay> generatedDays = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            List<Place> dayPlaces = pickDayPlaces(candidatePlaces, usedPlaceIds, trip, stopsPerDay, dayNumber);

            double walkKm = calculateWalkKm(dayPlaces.size(), trip.getPace());
            ItineraryDay day = new ItineraryDay(
                    trip,
                    dayNumber,
                    titleFor(dayNumber, dayPlaces),
                    summaryFor(dayPlaces, trip),
                    walkKm
            );

            int order = 1;
            for (Place place : dayPlaces) {
                day.addStop(new ItineraryStop(
                        order,
                        place.getName(),
                        place.getCategory().name(),
                        timeWindowFor(order),
                        noteFor(place, trip, dayNumber, order),
                        coordinateFor(place, dayNumber, order, true),
                        coordinateFor(place, dayNumber, order, false)
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

    private int stopsPerDay(Trip trip) {
        int base = switch (trip.getPace()) {
            case RELAXED -> 3;
            case BALANCED -> 4;
            case FULL -> 5;
        };
        if (trip.getBudget() == BudgetMode.LEAN && base > 3) {
            return base - 1;
        }
        return base;
    }

    private List<Place> selectPlaces(Trip trip) {
        Set<PlaceCategory> categories = categoriesFor(trip.getInterests());
        List<Place> allPlaces = placeRepository.findAll();
        List<Place> exactCityPlaces = filterPlaces(allPlaces, trip, categories, trip.getDestination());

        if (!exactCityPlaces.isEmpty()) {
            return arrangeForDailyRhythm(exactCityPlaces, trip);
        }

        List<Place> amsterdamFallback = filterPlaces(allPlaces, trip, categories, "Amsterdam");
        if (!amsterdamFallback.isEmpty()) {
            return arrangeForDailyRhythm(amsterdamFallback, trip);
        }

        return arrangeForDailyRhythm(
                allPlaces.stream()
                        .filter(place -> categories.contains(place.getCategory()))
                        .filter(place -> budgetAllows(trip.getBudget(), place.getPriceLevel()))
                        .sorted(Comparator.comparingDouble((Place place) -> scorePlace(place, trip, false)).reversed())
                        .toList(),
                trip
        );
    }

    private List<Place> filterPlaces(List<Place> places, Trip trip, Set<PlaceCategory> categories, String city) {
        return places.stream()
                .filter(place -> place.getCity().equalsIgnoreCase(city))
                .filter(place -> categories.contains(place.getCategory()))
                .filter(place -> budgetAllows(trip.getBudget(), place.getPriceLevel()))
                .sorted(Comparator.comparingDouble((Place place) -> scorePlace(place, trip, false)).reversed())
                .toList();
    }

    private List<Place> arrangeForDailyRhythm(List<Place> places, Trip trip) {
        List<PlaceCategory> rhythm = categoryRhythm(trip.getInterests());
        List<Place> remaining = new ArrayList<>(places);
        List<Place> arranged = new ArrayList<>();

        while (!remaining.isEmpty()) {
            boolean pickedInRound = false;
            for (PlaceCategory category : rhythm) {
                int matchIndex = firstIndexOfCategory(remaining, category);
                if (matchIndex >= 0) {
                    arranged.add(remaining.remove(matchIndex));
                    pickedInRound = true;
                }
            }

            if (!pickedInRound) {
                arranged.add(remaining.remove(0));
            }
        }

        return arranged;
    }

    private List<Place> pickDayPlaces(List<Place> candidatePlaces, Set<String> usedPlaceIds, Trip trip, int stopsPerDay, int dayNumber) {
        List<PlaceCategory> rhythm = rotateRhythm(categoryRhythm(trip.getInterests()), dayNumber - 1);
        List<Place> dayPlaces = new ArrayList<>();
        List<Place> placePool = candidatePlaces.stream()
                .filter(place -> !usedPlaceIds.contains(place.getId()))
                .sorted(Comparator.comparingDouble((Place place) -> scorePlace(place, trip, dayNumber == 1)).reversed())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (dayNumber == 1) {
            findStartingAreaMatch(placePool, trip).ifPresent(place -> {
                dayPlaces.add(place);
                usedPlaceIds.add(place.getId());
                placePool.remove(place);
            });
        }

        for (PlaceCategory category : rhythm) {
            if (dayPlaces.size() >= stopsPerDay || placePool.isEmpty()) {
                break;
            }
            int matchIndex = firstIndexOfCategory(placePool, category);
            if (matchIndex >= 0) {
                Place picked = placePool.remove(matchIndex);
                dayPlaces.add(picked);
                usedPlaceIds.add(picked.getId());
            }
        }

        while (dayPlaces.size() < stopsPerDay && !placePool.isEmpty()) {
            Place picked = placePool.remove(0);
            dayPlaces.add(picked);
            usedPlaceIds.add(picked.getId());
        }

        if (!hasFoodOrCoffee(dayPlaces) && !placePool.isEmpty()) {
            int breakIndex = firstIndexOfCategory(placePool, PlaceCategory.COFFEE);
            if (breakIndex < 0) {
                breakIndex = firstIndexOfCategory(placePool, PlaceCategory.FOOD);
            }
            if (breakIndex >= 0 && !dayPlaces.isEmpty()) {
                dayPlaces.remove(dayPlaces.size() - 1);
                Place picked = placePool.remove(breakIndex);
                dayPlaces.add(picked);
                usedPlaceIds.add(picked.getId());
            }
        }

        fillWithPlannedStops(dayPlaces, trip, rhythm, stopsPerDay, dayNumber);
        return dayPlaces;
    }

    private java.util.Optional<Place> findStartingAreaMatch(List<Place> places, Trip trip) {
        if (trip.getStartingArea() == null || trip.getStartingArea().isBlank()) {
            return java.util.Optional.empty();
        }
        String start = normalize(trip.getStartingArea());
        return places.stream()
                .filter(place -> normalize(place.getName()).contains(start)
                        || normalize(place.getAddress()).contains(start)
                        || normalize(place.getTags()).contains(start))
                .max(Comparator.comparingDouble((Place place) -> scorePlace(place, trip, true)));
    }

    private void fillWithPlannedStops(List<Place> dayPlaces, Trip trip, List<PlaceCategory> rhythm, int stopsPerDay, int dayNumber) {
        int rhythmIndex = 0;
        while (dayPlaces.size() < stopsPerDay) {
            PlaceCategory category = rhythm.get(rhythmIndex % rhythm.size());
            if (category == PlaceCategory.FREE && trip.getBudget() != BudgetMode.LEAN && dayPlaces.size() > 1) {
                category = PlaceCategory.WALKING;
            }
            dayPlaces.add(plannedStop(trip, category, dayNumber, dayPlaces.size() + 1));
            rhythmIndex++;
        }
    }

    private List<PlaceCategory> rotateRhythm(List<PlaceCategory> rhythm, int offset) {
        if (rhythm.isEmpty()) {
            return rhythm;
        }
        int shift = offset % rhythm.size();
        List<PlaceCategory> rotated = new ArrayList<>(rhythm.subList(shift, rhythm.size()));
        rotated.addAll(rhythm.subList(0, shift));
        return rotated;
    }

    private List<PlaceCategory> categoryRhythm(List<TravelInterest> interests) {
        List<PlaceCategory> rhythm = new ArrayList<>();
        if (interests.contains(TravelInterest.MUSEUMS) || interests.contains(TravelInterest.CULTURE)) {
            rhythm.add(PlaceCategory.CULTURE);
        }
        if (interests.contains(TravelInterest.WALKING)) {
            rhythm.add(PlaceCategory.WALKING);
        }
        if (interests.contains(TravelInterest.COFFEE)) {
            rhythm.add(PlaceCategory.COFFEE);
        }
        if (interests.contains(TravelInterest.LOCAL_FOOD)) {
            rhythm.add(PlaceCategory.FOOD);
        }
        if (interests.contains(TravelInterest.FREE_ACTIVITIES)) {
            rhythm.add(PlaceCategory.FREE);
        }

        for (PlaceCategory category : List.of(PlaceCategory.CULTURE, PlaceCategory.WALKING, PlaceCategory.COFFEE, PlaceCategory.FOOD, PlaceCategory.FREE)) {
            if (!rhythm.contains(category)) {
                rhythm.add(category);
            }
        }
        return rhythm;
    }

    private int firstIndexOfCategory(List<Place> places, PlaceCategory category) {
        for (int index = 0; index < places.size(); index++) {
            if (places.get(index).getCategory() == category) {
                return index;
            }
        }
        return -1;
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

    private double scorePlace(Place place, Trip trip, boolean firstDay) {
        double score = place.getRating();
        if (categoryMatchesInterest(place.getCategory(), trip.getInterests())) {
            score += 1.2;
        }
        if (budgetAllows(trip.getBudget(), place.getPriceLevel())) {
            score += 0.4;
        }
        if (trip.getPace().name().equals("RELAXED") && place.getCategory() == PlaceCategory.WALKING) {
            score -= 0.2;
        }
        if (trip.getPace().name().equals("FULL") && place.getCategory() == PlaceCategory.CULTURE) {
            score += 0.2;
        }
        if (firstDay && startingAreaMatches(place, trip)) {
            score += 2.0;
        }
        if (trip.getBudget() == BudgetMode.LEAN && place.getCategory() == PlaceCategory.FREE) {
            score += 0.8;
        }
        if (trip.getBudget() == BudgetMode.COMFORT && place.getCategory() == PlaceCategory.FOOD) {
            score += 0.3;
        }
        return score;
    }

    private boolean startingAreaMatches(Place place, Trip trip) {
        if (trip.getStartingArea() == null || trip.getStartingArea().isBlank()) {
            return false;
        }
        String start = normalize(trip.getStartingArea());
        return normalize(place.getName()).contains(start)
                || normalize(place.getAddress()).contains(start)
                || normalize(place.getTags()).contains(start);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }

    private boolean categoryMatchesInterest(PlaceCategory category, List<TravelInterest> interests) {
        return switch (category) {
            case COFFEE -> interests.contains(TravelInterest.COFFEE);
            case FOOD -> interests.contains(TravelInterest.LOCAL_FOOD);
            case CULTURE -> interests.contains(TravelInterest.MUSEUMS) || interests.contains(TravelInterest.CULTURE);
            case WALKING -> interests.contains(TravelInterest.WALKING);
            case FREE -> interests.contains(TravelInterest.FREE_ACTIVITIES);
        };
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

    private Place plannedStop(Trip trip, PlaceCategory category, int dayNumber, int order) {
        String city = trip.getDestination();
        String label = switch (category) {
            case CULTURE -> "Culture window";
            case COFFEE -> "Coffee pause";
            case FOOD -> order >= 4 ? "Dinner zone" : "Local food stop";
            case WALKING -> "Neighborhood walk";
            case FREE -> "Free local moment";
        };
        String note = switch (category) {
            case CULTURE -> "A curated culture block kept close to the route so the day stays realistic.";
            case COFFEE -> "A short break window added to prevent the plan from feeling rushed.";
            case FOOD -> "A food stop selected to match your budget and keep transfers short.";
            case WALKING -> "A flexible walk that connects nearby areas without adding a reservation.";
            case FREE -> "A low-cost local experience that keeps the day useful without stretching the budget.";
        };
        return new Place(
                city + " " + label + " " + dayNumber,
                city,
                category,
                note,
                priceLevelFor(category, trip.getBudget()),
                4.5,
                ""
        );
    }

    private String priceLevelFor(PlaceCategory category, BudgetMode budget) {
        if (category == PlaceCategory.FREE || budget == BudgetMode.LEAN) {
            return category == PlaceCategory.FOOD ? "Lean" : "Free";
        }
        if (budget == BudgetMode.COMFORT && category == PlaceCategory.FOOD) {
            return "Comfort";
        }
        return "Mid";
    }

    private String titleFor(int dayNumber, List<Place> places) {
        boolean hasFood = places.stream().anyMatch(place -> place.getCategory() == PlaceCategory.FOOD);
        boolean hasCulture = places.stream().anyMatch(place -> place.getCategory() == PlaceCategory.CULTURE);
        if (hasFood && hasCulture) return "Culture & local food";
        if (hasCulture) return "Culture-led city day";
        if (hasFood) return "Local food route";
        return "Balanced city day " + dayNumber;
    }

    private String summaryFor(List<Place> places, Trip trip) {
        int stopCount = places.size();
        String paceLabel = trip.getPace().name().toLowerCase().replace('_', ' ');
        String startContext = trip.getStartingArea() == null || trip.getStartingArea().isBlank()
                ? "your starting point"
                : trip.getStartingArea();
        String budgetLabel = trip.getBudget().name().toLowerCase();
        return "A " + paceLabel + ", " + budgetLabel + " day from " + startContext + " with " + stopCount + " stops, local breaks and enough room to adjust the pace.";
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

    private String noteFor(Place place, Trip trip, int dayNumber, int order) {
        String duration = place.getEstimatedVisitMinutes() == null ? "" : " Suggested visit: " + place.getEstimatedVisitMinutes() + " min.";
        String start = dayNumber == 1 && order == 1 && trip.getStartingArea() != null && !trip.getStartingArea().isBlank()
                ? " Starts near " + trip.getStartingArea() + "."
                : "";
        return place.getDescription() + duration + start;
    }

    private double calculateWalkKm(int stopCount, com.journy.backend.trip.enums.TripPace pace) {
        double base = switch (pace) {
            case RELAXED -> 1.1;
            case BALANCED -> 1.35;
            case FULL -> 1.55;
        };
        return Math.round(stopCount * base * 10.0) / 10.0;
    }

    private boolean hasFoodOrCoffee(List<Place> places) {
        return places.stream().anyMatch(place -> place.getCategory() == PlaceCategory.FOOD || place.getCategory() == PlaceCategory.COFFEE);
    }

    private double coordinateFor(Place place, int dayNumber, int order, boolean latitude) {
        Double coordinate = latitude ? place.getLatitude() : place.getLongitude();
        double base = coordinate != null ? coordinate : latitude ? 52.3676 : 4.9041;
        double delta = (dayNumber * 0.004) + (order * 0.002);
        if (coordinate != null) {
            return latitude ? base - delta : base + delta;
        }
        return latitude ? base - delta : base + delta;
    }
}
