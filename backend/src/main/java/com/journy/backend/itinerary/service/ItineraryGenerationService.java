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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        generate(trip);
    }

    public void regenerate(Trip trip) {
        itineraryDayRepository.deleteByTripId(trip.getId());
        generate(trip);
    }

    private void generate(Trip trip) {
        List<Place> candidatePlaces = selectPlaces(trip);
        Set<String> usedPlaceIds = new HashSet<>();
        int days = trip.dayCount();
        int stopsPerDay = stopsPerDay(trip);

        List<ItineraryDay> generatedDays = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            List<Place> dayPlaces = pickDayPlaces(candidatePlaces, usedPlaceIds, trip, stopsPerDay, dayNumber);
            DayTheme theme = dynamicThemeFor(trip, dayNumber, dayPlaces);

            double walkKm = calculateWalkKm(dayPlaces.size(), trip.getPace(), dayNumber, dayPlaces);
            ItineraryDay day = new ItineraryDay(
                    trip,
                    dayNumber,
                    titleFor(theme, dayPlaces),
                    summaryFor(theme, dayPlaces, trip),
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

        return List.of();
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
        List<PlaceCategory> rhythm = dayRhythm(trip, dayNumber);
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
        int rhythmIndex = dayPlaces.size();
        while (dayPlaces.size() < stopsPerDay) {
            PlaceCategory category = rhythm.get(rhythmIndex % rhythm.size());
            if (category == PlaceCategory.FREE && trip.getBudget() != BudgetMode.LEAN && dayPlaces.size() > 1) {
                category = PlaceCategory.WALKING;
            }
            dayPlaces.add(plannedStop(trip, category, dayNumber, dayPlaces.size() + 1));
            rhythmIndex++;
        }
    }

    private List<PlaceCategory> dayRhythm(Trip trip, int dayNumber) {
        List<PlaceCategory> base = rotateRhythm(categoryRhythm(trip.getInterests()), dayNumber - 1);
        List<PlaceCategory> themeRhythm = themeRhythm(trip, dayNumber);
        List<PlaceCategory> rhythm = new ArrayList<>(themeRhythm);
        for (PlaceCategory category : base) {
            if (!rhythm.contains(category)) {
                rhythm.add(category);
            }
        }
        return rhythm;
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
        String label = plannedStopName(city, category, dayNumber, order);
        String note = switch (category) {
            case CULTURE -> "A culture anchor placed into this day's theme so the route has a clear purpose.";
            case COFFEE -> "A compact break window that keeps the pace comfortable between bigger stops.";
            case FOOD -> order >= 4
                    ? "A dinner area chosen to close the day without a long transfer."
                    : "A local food stop selected to match your budget and keep the day grounded.";
            case WALKING -> "A walkable connector that gives the day texture without adding reservation pressure.";
            case FREE -> "A low-cost local window that keeps the route useful and flexible.";
        };
        return new Place(
                label,
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

    private String titleFor(DayTheme theme, List<Place> places) {
        if (theme != null && theme.title() != null && !theme.title().isBlank()) {
            return theme.title();
        }
        return "Local City Loop";
    }

    private String summaryFor(DayTheme theme, List<Place> places, Trip trip) {
        int stopCount = places.size();
        String paceLabel = trip.getPace().name().toLowerCase().replace('_', ' ');
        String startContext = trip.getStartingArea() == null || trip.getStartingArea().isBlank()
                ? "your starting point"
                : trip.getStartingArea();
        String budgetLabel = trip.getBudget().name().toLowerCase();
        String themeSummary = theme == null ? "the route balances local anchors, breaks and flexible walking" : theme.summary();
        return "A " + paceLabel + ", " + budgetLabel + " day from " + startContext + " where " + themeSummary + ". Planned with " + stopCount + " stops and enough room to adjust the pace.";
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

    private double calculateWalkKm(int stopCount, com.journy.backend.trip.enums.TripPace pace, int dayNumber, List<Place> places) {
        double base = switch (pace) {
            case RELAXED -> 1.1;
            case BALANCED -> 1.35;
            case FULL -> 1.55;
        };
        double categoryAdjustment = places.stream()
                .mapToDouble(place -> switch (place.getCategory()) {
                    case WALKING, FREE -> 0.18;
                    case CULTURE -> 0.08;
                    case FOOD -> -0.05;
                    case COFFEE -> -0.12;
                })
                .sum();
        double dayVariation = switch (dayNumber % 4) {
            case 1 -> 0.0;
            case 2 -> -0.25;
            case 3 -> 0.35;
            default -> 0.15;
        };
        return Math.max(2.4, Math.round((stopCount * base + categoryAdjustment + dayVariation) * 10.0) / 10.0);
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

    private DayTheme dynamicThemeFor(Trip trip, int dayNumber, List<Place> places) {
        List<PlaceCategory> rhythm = dayRhythm(trip, dayNumber);
        Map<PlaceCategory, Place> anchors = anchorsByCategory(places);
        PlaceCategory lead = leadCategory(rhythm, anchors);
        PlaceCategory support = supportCategory(rhythm, anchors, lead);
        String leadLabel = titleLabel(trip, anchors.get(lead), lead, dayNumber);
        String supportLabel = support == null ? paceLabel(trip) : categoryPhrase(support, anchors.get(support));
        String title = leadLabel + " & " + supportLabel;
        String summary = summaryPhrase(lead, support, trip);
        return new DayTheme(title, summary, rhythm);
    }

    private Map<PlaceCategory, Place> anchorsByCategory(List<Place> places) {
        Map<PlaceCategory, Place> anchors = new LinkedHashMap<>();
        for (Place place : places) {
            anchors.putIfAbsent(place.getCategory(), place);
        }
        return anchors;
    }

    private PlaceCategory leadCategory(List<PlaceCategory> rhythm, Map<PlaceCategory, Place> anchors) {
        for (PlaceCategory category : rhythm) {
            if (anchors.containsKey(category)) {
                return category;
            }
        }
        return anchors.keySet().stream().findFirst().orElse(PlaceCategory.WALKING);
    }

    private PlaceCategory supportCategory(List<PlaceCategory> rhythm, Map<PlaceCategory, Place> anchors, PlaceCategory lead) {
        for (PlaceCategory category : rhythm) {
            if (category != lead && anchors.containsKey(category)) {
                return category;
            }
        }
        return null;
    }

    private String titleLabel(Trip trip, Place place, PlaceCategory category, int dayNumber) {
        String area = place == null ? trip.getDestination() : readableAnchor(place.getName(), trip.getDestination());
        if (area == null || area.isBlank()) {
            area = trip.getDestination();
        }
        return switch (category) {
            case CULTURE -> area.contains("Museum") || area.contains("Design") || area.contains("Gallery")
                    ? area
                    : area + " Culture";
            case WALKING, FREE -> area.contains("Walk") || area.contains("Loop") || area.contains("Garden") || area.contains("View")
                    ? area
                    : area + " Walk";
            case COFFEE -> area.contains("Coffee") || area.contains("Cafe") || area.contains("Bakery")
                    ? area
                    : area + " Coffee";
            case FOOD -> area.contains("Lunch") || area.contains("Dinner") || area.contains("Market") || area.contains("Food")
                    ? area
                    : area + " Food";
        };
    }

    private String categoryPhrase(PlaceCategory category, Place place) {
        return switch (category) {
            case CULTURE -> compactPlacePhrase(place, "Culture");
            case WALKING -> compactPlacePhrase(place, "Slow Walk");
            case FREE -> compactPlacePhrase(place, "Free Views");
            case COFFEE -> compactPlacePhrase(place, "Coffee Break");
            case FOOD -> compactPlacePhrase(place, "Local Food");
        };
    }

    private String compactPlacePhrase(Place place, String fallback) {
        if (place == null || place.getName() == null || place.getName().isBlank()) {
            return fallback;
        }
        String name = place.getName();
        if (name.length() <= 22) {
            return name;
        }
        return fallback;
    }

    private String readableAnchor(String name, String city) {
        if (name == null || name.isBlank()) {
            return city;
        }
        String cleaned = name
                .replace(city, "")
                .replace("Window", "")
                .replace("Stop", "")
                .replace("Pause", "")
                .replace("Anchor", "")
                .trim();
        return cleaned.isBlank() ? name : cleaned;
    }

    private String paceLabel(Trip trip) {
        return switch (trip.getPace()) {
            case RELAXED -> "Slow Pacing";
            case BALANCED -> "Easy Route";
            case FULL -> "Full City Flow";
        };
    }

    private String summaryPhrase(PlaceCategory lead, PlaceCategory support, Trip trip) {
        String pace = trip.getPace().name().toLowerCase();
        String leadText = switch (lead) {
            case CULTURE -> "culture anchors shape the first half of the day";
            case WALKING -> "walkable connectors define the route";
            case FREE -> "low-cost local windows keep the day flexible";
            case COFFEE -> "coffee breaks create softer transitions";
            case FOOD -> "food stops give the day a local rhythm";
        };
        String supportText = support == null ? "the remaining stops keep the plan balanced" : switch (support) {
            case CULTURE -> "culture stays close enough to avoid route sprawl";
            case WALKING -> "short walking links connect the main stops";
            case FREE -> "free moments reduce budget pressure";
            case COFFEE -> "breaks are placed before the day gets too dense";
            case FOOD -> "food is timed as a practical route anchor";
        };
        return leadText + " and " + supportText + " for a " + pace + " pace";
    }

    private List<PlaceCategory> themeRhythm(Trip trip, int dayNumber) {
        boolean wantsFood = trip.getInterests().contains(TravelInterest.LOCAL_FOOD);
        boolean wantsCoffee = trip.getInterests().contains(TravelInterest.COFFEE);
        boolean wantsCulture = trip.getInterests().contains(TravelInterest.MUSEUMS) || trip.getInterests().contains(TravelInterest.CULTURE);
        boolean wantsWalking = trip.getInterests().contains(TravelInterest.WALKING);
        int slot = (dayNumber - 1) % 5;

        if (slot == 0 && wantsWalking) {
            return List.of(PlaceCategory.WALKING, PlaceCategory.COFFEE, PlaceCategory.CULTURE, PlaceCategory.FOOD, PlaceCategory.FREE);
        }
        if (slot == 0 && wantsCulture) {
            return List.of(PlaceCategory.CULTURE, PlaceCategory.COFFEE, PlaceCategory.WALKING, PlaceCategory.FOOD, PlaceCategory.FREE);
        }
        if (slot == 1 && (wantsCulture || wantsCoffee)) {
            return List.of(PlaceCategory.CULTURE, PlaceCategory.COFFEE, PlaceCategory.FREE, PlaceCategory.FOOD, PlaceCategory.WALKING);
        }
        if (slot == 2 && wantsFood) {
            return List.of(PlaceCategory.FOOD, PlaceCategory.WALKING, PlaceCategory.COFFEE, PlaceCategory.CULTURE, PlaceCategory.FREE);
        }
        if (slot == 3 && trip.getBudget() == BudgetMode.LEAN) {
            return List.of(PlaceCategory.FREE, PlaceCategory.FOOD, PlaceCategory.WALKING, PlaceCategory.COFFEE, PlaceCategory.CULTURE);
        }
        if (slot == 3) {
            return List.of(PlaceCategory.WALKING, PlaceCategory.FOOD, PlaceCategory.COFFEE, PlaceCategory.CULTURE, PlaceCategory.FREE);
        }
        if (wantsCoffee) {
            return List.of(PlaceCategory.COFFEE, PlaceCategory.CULTURE, PlaceCategory.WALKING, PlaceCategory.FOOD, PlaceCategory.FREE);
        }
        return List.of(PlaceCategory.WALKING, PlaceCategory.CULTURE, PlaceCategory.COFFEE, PlaceCategory.FOOD, PlaceCategory.FREE);
    }

    private String plannedStopName(String city, PlaceCategory category, int dayNumber, int order) {
        List<String> names = cityStopNames(city, category);
        int index = Math.floorMod(dayNumber + order - 2, names.size());
        return names.get(index);
    }

    private List<String> cityStopNames(String city, PlaceCategory category) {
        Map<PlaceCategory, List<String>> cityNames = switch (normalize(city)) {
            case "copenhagen" -> Map.of(
                    PlaceCategory.WALKING, List.of("Christianshavn Canal Walk", "Harbor Bath Stroll", "Frederiksberg Garden Loop", "Vesterbro Design Walk"),
                    PlaceCategory.COFFEE, List.of("Norrebro Coffee Break", "Vesterbro Bakery Pause", "Indre By Espresso Stop", "Christianshavn Cafe Window"),
                    PlaceCategory.FOOD, List.of("Torvehallerne Lunch Window", "Meatpacking Dinner Zone", "Reffen Street Food Stop", "Norrebro Local Dinner"),
                    PlaceCategory.CULTURE, List.of("Designmuseum Culture Window", "SMK Morning Block", "Copenhagen Architecture Center", "Kunsthal Charlottenborg Stop"),
                    PlaceCategory.FREE, List.of("King's Garden Reset", "Superkilen Color Walk", "Lakeside Free Window", "Ofelia Plads Viewpoint")
            );
            case "berlin" -> Map.of(
                    PlaceCategory.WALKING, List.of("Spree River Walk", "Kreuzberg Street Loop", "Tiergarten Green Route", "Prenzlauer Berg Slow Walk"),
                    PlaceCategory.COFFEE, List.of("Kreuzberg Coffee Break", "Mitte Espresso Window", "Neukolln Cafe Pause", "Prenzlauer Berg Bakery Stop"),
                    PlaceCategory.FOOD, List.of("Markthalle Lunch", "Kreuzberg Dinner Zone", "Street Food Thursday Window", "Mitte Local Bites"),
                    PlaceCategory.CULTURE, List.of("Museum Island Anchor", "Berlinische Galerie Window", "Bauhaus Archive Stop", "East Side Gallery Stretch"),
                    PlaceCategory.FREE, List.of("Tempelhofer Feld Reset", "Tiergarten Free Window", "Spree Viewpoint", "Mauerpark Local Moment")
            );
            case "istanbul" -> Map.of(
                    PlaceCategory.WALKING, List.of("Karakoy Gallery Walk", "Bosphorus Shore Loop", "Balat Color Streets", "Moda Seaside Walk"),
                    PlaceCategory.COFFEE, List.of("Karakoy Coffee Break", "Cihangir Cafe Window", "Kadikoy Roaster Stop", "Balat Tea Pause"),
                    PlaceCategory.FOOD, List.of("Kadikoy Food Streets", "Karakoy Dinner Window", "Cukurcuma Local Lunch", "Besiktas Breakfast Stop"),
                    PlaceCategory.CULTURE, List.of("Sultanahmet Culture Anchor", "Pera Museum Window", "Istanbul Modern Stop", "Balat Heritage Walk"),
                    PlaceCategory.FREE, List.of("Bosphorus Ferry Window", "Gulhane Garden Reset", "Galata Viewpoint", "Moda Sunset Stop")
            );
            default -> Map.of(
                    PlaceCategory.WALKING, List.of(city + " Waterfront Walk", city + " Old Town Loop", city + " Design District Walk", city + " Garden Route"),
                    PlaceCategory.COFFEE, List.of(city + " Coffee Break", city + " Bakery Pause", city + " Espresso Window", city + " Cafe Stop"),
                    PlaceCategory.FOOD, List.of(city + " Market Lunch", city + " Local Dinner Zone", city + " Food Street Stop", city + " Neighborhood Bites"),
                    PlaceCategory.CULTURE, List.of(city + " Culture Anchor", city + " Museum Window", city + " Gallery Block", city + " Design Stop"),
                    PlaceCategory.FREE, List.of(city + " Free Viewpoint", city + " Park Reset", city + " Public Square Window", city + " Scenic Pause")
            );
        };
        return cityNames.get(category);
    }

    private record DayTheme(String title, String summary, List<PlaceCategory> rhythm) {
    }
}
