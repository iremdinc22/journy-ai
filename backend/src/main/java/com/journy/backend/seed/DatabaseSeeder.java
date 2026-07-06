package com.journy.backend.seed;

import com.journy.backend.destination.model.Destination;
import com.journy.backend.destination.repository.DestinationRepository;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.notification.model.AppNotification;
import com.journy.backend.notification.repository.AppNotificationRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@Profile("dev")
public class DatabaseSeeder implements CommandLineRunner {
    private final UserAccountRepository userAccountRepository;
    private final TripRepository tripRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final PlaceRepository placeRepository;
    private final DestinationRepository destinationRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(
            UserAccountRepository userAccountRepository,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            PlaceRepository placeRepository,
            DestinationRepository destinationRepository,
            AppNotificationRepository appNotificationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.placeRepository = placeRepository;
        this.destinationRepository = destinationRepository;
        this.appNotificationRepository = appNotificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedDestinations();
        seedPlaces();

        if (userAccountRepository.existsByEmailIgnoreCase("admin@journy.app")) {
            return;
        }

        UserAccount user = userAccountRepository.save(new UserAccount(
                "Irem Dinc",
                "admin@journy.app",
                passwordEncoder.encode("admin123"),
                "Balanced traveler"
        ));

        Trip amsterdam = new Trip(
                user,
                "Amsterdam",
                "Centraal Station",
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 14),
                TravelerType.COUPLE,
                BudgetMode.BALANCED,
                TripPace.BALANCED,
                Set.of(TravelInterest.COFFEE, TravelInterest.MUSEUMS, TravelInterest.LOCAL_FOOD, TravelInterest.WALKING)
        );
        amsterdam.setCurrentTrip(true);
        amsterdam.setTotalStops(18);
        amsterdam.setFoodPicks(7);
        amsterdam.setAverageWalkKm(6.2);
        tripRepository.save(amsterdam);

        Trip rome = new Trip(
                user,
                "Rome",
                "Trastevere",
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 9),
                TravelerType.FRIENDS,
                BudgetMode.COMFORT,
                TripPace.FULL,
                Set.of(TravelInterest.LOCAL_FOOD, TravelInterest.CULTURE, TravelInterest.WALKING)
        );
        rome.setCurrentTrip(false);
        rome.setTotalStops(12);
        rome.setFoodPicks(8);
        rome.setAverageWalkKm(4.9);
        tripRepository.save(rome);

        seedItinerary(amsterdam);
        seedNotifications(user);
    }

    private void seedDestinations() {
        List.of(
                destination(
                        "Amsterdam",
                        "Netherlands",
                        "Canal neighborhoods, compact museums and calm food streets make Amsterdam ideal for walkable AI-planned days.",
                        "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=88",
                        "Canals, Coffee, Museums",
                        "Coffee breaks, museums, easy walking",
                        9,
                        6.2,
                        true,
                        true
                ),
                destination(
                        "Paris",
                        "France",
                        "A culture and bakery-heavy city where Journy can group museums, river walks and local food windows by neighborhood.",
                        "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=88",
                        "Museums, Bakeries, Walks",
                        "Art, pastries, scenic routes",
                        4,
                        5.8,
                        true,
                        true
                ),
                destination(
                        "Rome",
                        "Italy",
                        "Historic anchors and food-first neighborhoods work well for compact days with dinner zones near the final stop.",
                        "https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=88",
                        "History, Piazzas, Dinner",
                        "Local food, history, evening walks",
                        4,
                        4.9,
                        true,
                        true
                ),
                destination(
                        "Barcelona",
                        "Spain",
                        "Design districts, beach walks and tapas streets make Barcelona strong for mixed culture and food routes.",
                        "https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=88",
                        "Design, Beach, Tapas",
                        "Design, markets, relaxed walking",
                        4,
                        5.2,
                        true,
                        true
                ),
                destination(
                        "Tokyo",
                        "Japan",
                        "Tokyo is on the roadmap. Journy can create a draft now, but curated local data is not fully available yet.",
                        "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=900&q=88",
                        "Food, Neighborhoods, Transit",
                        "Draft planning only",
                        0,
                        7.1,
                        false,
                        false
                ),
                destination(
                        "London",
                        "United Kingdom",
                        "London works well for neighborhood-led days across galleries, markets, parks and dinner streets.",
                        "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=900&q=88",
                        "Markets, Parks, Culture",
                        "Free museums, coffee, food halls",
                        6,
                        6.7,
                        true,
                        true
                ),
                destination(
                        "Lisbon",
                        "Portugal",
                        "Lisbon is strong for scenic walks, small cafes, viewpoints and food routes with a relaxed pace.",
                        "https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=900&q=88",
                        "Views, Cafes, Seafood",
                        "Slow walks, bakeries, viewpoints",
                        6,
                        5.6,
                        true,
                        true
                ),
                destination(
                        "Prague",
                        "Czechia",
                        "Prague is ideal for compact culture days, riverside walks and atmospheric local food stops.",
                        "https://images.unsplash.com/photo-1519677100203-a0e668c92439?auto=format&fit=crop&w=900&q=88",
                        "Old Town, River, Cafes",
                        "Culture, walking, low-cost days",
                        6,
                        5.4,
                        true,
                        true
                ),
                destination(
                        "Vienna",
                        "Austria",
                        "Vienna supports premium-feeling culture routes with cafes, museums, markets and calm transit windows.",
                        "https://images.unsplash.com/photo-1516550893923-42d28e5677af?auto=format&fit=crop&w=900&q=88",
                        "Museums, Cafes, Markets",
                        "Art, pastries, elegant walks",
                        6,
                        5.9,
                        true,
                        true
                )
        ).forEach(this::upsertDestination);
    }

    private Destination destination(
            String name,
            String country,
            String description,
            String imageUrl,
            String tags,
            String bestFor,
            int placeCount,
            double averageDailyWalkKm,
            boolean available,
            boolean popular
    ) {
        return new Destination(name, country, description, imageUrl, tags, bestFor, placeCount, averageDailyWalkKm, available, popular);
    }

    private void upsertDestination(Destination seed) {
        Destination destination = destinationRepository.findByNameIgnoreCase(seed.getName()).orElse(seed);
        destination.setCountry(seed.getCountry());
        destination.setDescription(seed.getDescription());
        destination.setImageUrl(seed.getImageUrl());
        destination.setTags(seed.getTags());
        destination.setBestFor(seed.getBestFor());
        destination.setPlaceCount(seed.getPlaceCount());
        destination.setAverageDailyWalkKm(seed.getAverageDailyWalkKm());
        destination.setAvailable(seed.isAvailable());
        destination.setPopular(seed.isPopular());
        destinationRepository.save(destination);
    }

    private void seedItinerary(Trip trip) {
        ItineraryDay dayOne = new ItineraryDay(
                trip,
                1,
                "Canals & Museums",
                "A calm first day with a museum window, canal walk and low-effort dinner area.",
                6.4
        );
        dayOne.addStop(new ItineraryStop(1, "Museumplein", "Culture", "09:30", "Start with the strongest anchor stop.", 52.3584, 4.8811));
        dayOne.addStop(new ItineraryStop(2, "Morning coffee", "Coffee", "11:30", "A quiet break before walking.", 52.3568, 4.8897));
        dayOne.addStop(new ItineraryStop(3, "Canal loop", "Walking", "14:00", "Scenic route with flexible pacing.", 52.3676, 4.9041));
        dayOne.addStop(new ItineraryStop(4, "De Pijp dinner", "Food", "19:00", "Local dinner area with easy transit back.", 52.3542, 4.8975));

        ItineraryDay dayTwo = new ItineraryDay(
                trip,
                2,
                "Historic Center",
                "Culture and food grouped tightly so the day feels rich without becoming exhausting.",
                4.8
        );
        dayTwo.addStop(new ItineraryStop(1, "Morning piazza", "Walking", "10:00", "Start central and keep transfers short.", 52.3731, 4.8922));
        dayTwo.addStop(new ItineraryStop(2, "Local bakery", "Food", "11:15", "Small food stop before the busiest part.", 52.3712, 4.8951));
        dayTwo.addStop(new ItineraryStop(3, "Gallery window", "Culture", "13:00", "A compact indoor culture stop.", 52.3698, 4.9010));
        dayTwo.addStop(new ItineraryStop(4, "Dinner canal edge", "Food", "19:00", "Stay close to the final walking area.", 52.3702, 4.8992));

        itineraryDayRepository.saveAll(List.of(dayOne, dayTwo));
    }

    private void seedPlaces() {
        List.of(
                new Place("Local Bistro Noord", "Amsterdam", PlaceCategory.FOOD, "Seasonal plates in a calm neighborhood room.", "Mid", 4.7, "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=700&q=85"),
                new Place("Canal Bakery", "Amsterdam", PlaceCategory.FOOD, "Morning pastries and simple coffee near the route.", "Lean", 4.6, "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=700&q=85"),
                new Place("Museumplein Window", "Amsterdam", PlaceCategory.CULTURE, "A compact culture stop that keeps the day balanced.", "Mid", 4.8, "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=700&q=85"),
                new Place("Small Gallery Walk", "Amsterdam", PlaceCategory.CULTURE, "Independent galleries close to cafe streets.", "Lean", 4.5, "https://images.unsplash.com/photo-1564399579883-451a5d44ec08?auto=format&fit=crop&w=700&q=85"),
                new Place("Quiet Cup De Pijp", "Amsterdam", PlaceCategory.COFFEE, "A soft-paced cafe for planning the next stop.", "Lean", 4.7, "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85"),
                new Place("Canal Roast Bar", "Amsterdam", PlaceCategory.COFFEE, "Good espresso without a long detour.", "Mid", 4.6, "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85"),
                new Place("Golden Hour Canal Loop", "Amsterdam", PlaceCategory.FREE, "A scenic walking loop with no reservation needed.", "Free", 4.8, "https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85"),
                new Place("Neighborhood Market", "Amsterdam", PlaceCategory.FREE, "Low-cost local browsing and quick bites.", "Free", 4.5, "https://images.unsplash.com/photo-1525968902-070804c45d6b?auto=format&fit=crop&w=700&q=85"),
                new Place("Jordaan Slow Walk", "Amsterdam", PlaceCategory.WALKING, "A quiet canal-side walking window with local streets and small shops.", "Free", 4.7, "https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85"),

                new Place("Left Bank Museum Window", "Paris", PlaceCategory.CULTURE, "A focused culture stop that pairs well with a river walk.", "Mid", 4.8, "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=700&q=85"),
                new Place("Saint-Germain Coffee Pause", "Paris", PlaceCategory.COFFEE, "A calm cafe break between gallery streets and the Seine.", "Mid", 4.7, "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85"),
                new Place("Canal Bakery Stop", "Paris", PlaceCategory.FOOD, "Morning pastry stop that keeps the route light and local.", "Lean", 4.6, "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=700&q=85"),
                new Place("Seine Golden Hour Walk", "Paris", PlaceCategory.WALKING, "A free scenic route for sunset without adding reservations.", "Free", 4.8, "https://images.unsplash.com/photo-1499856871958-5b9627545d1a?auto=format&fit=crop&w=700&q=85"),

                new Place("Monti Gallery Morning", "Rome", PlaceCategory.CULTURE, "Compact culture before lunch, close to food streets.", "Lean", 4.7, "https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=700&q=85"),
                new Place("Testaccio Table", "Rome", PlaceCategory.FOOD, "Local plates in a neighborhood built for food-first days.", "Mid", 4.8, "https://images.unsplash.com/photo-1533777857889-4be7c70b33f7?auto=format&fit=crop&w=700&q=85"),
                new Place("Central Espresso Bar", "Rome", PlaceCategory.COFFEE, "A short espresso break between historic anchors.", "Lean", 4.6, "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=700&q=85"),
                new Place("Trastevere Evening Walk", "Rome", PlaceCategory.WALKING, "Low-effort streets for dinner and a flexible evening.", "Free", 4.7, "https://images.unsplash.com/photo-1529260830199-42c24126f198?auto=format&fit=crop&w=700&q=85"),

                new Place("Born Design Route", "Barcelona", PlaceCategory.CULTURE, "Independent galleries and design shops grouped into one area.", "Lean", 4.6, "https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=700&q=85"),
                new Place("Gracia Coffee Corner", "Barcelona", PlaceCategory.COFFEE, "A softer cafe stop away from the busiest tourist streets.", "Lean", 4.7, "https://images.unsplash.com/photo-1511920170033-f8396924c348?auto=format&fit=crop&w=700&q=85"),
                new Place("Tapas Street Window", "Barcelona", PlaceCategory.FOOD, "Easy dinner zone with short transfers after the final stop.", "Mid", 4.8, "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=700&q=85"),
                new Place("Gothic Quarter Drift", "Barcelona", PlaceCategory.FREE, "Free wandering through small lanes, plazas and local corners.", "Free", 4.6, "https://images.unsplash.com/photo-1539037116277-4db20889f2d4?auto=format&fit=crop&w=700&q=85"),

                new Place("South Bank Culture Walk", "London", PlaceCategory.CULTURE, "A flexible riverside culture route with galleries and easy food options nearby.", "Free", 4.8, "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=700&q=85", "South Bank, London", 51.5076, -0.0994, "10:00 - 18:00", 120, "culture,free,walkable"),
                new Place("Borough Market Lunch", "London", PlaceCategory.FOOD, "A high-choice food stop that works well for mixed budgets and short detours.", "Mid", 4.7, "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=700&q=85", "Borough Market, London", 51.5055, -0.0910, "10:00 - 17:00", 75, "food,market,local"),
                new Place("Soho Coffee Pause", "London", PlaceCategory.COFFEE, "A compact coffee break between central culture and evening areas.", "Mid", 4.6, "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85", "Soho, London", 51.5136, -0.1365, "08:00 - 18:00", 45, "coffee,central,break"),
                new Place("Hyde Park Soft Window", "London", PlaceCategory.FREE, "A free open-air reset that keeps a full city day from becoming too dense.", "Free", 4.6, "https://images.unsplash.com/photo-1533929736458-ca588d08c8be?auto=format&fit=crop&w=700&q=85", "Hyde Park, London", 51.5073, -0.1657, "Open route window", 70, "free,walking,park"),

                new Place("Alfama Morning Walk", "Lisbon", PlaceCategory.WALKING, "A scenic slow-start route through viewpoints, tiled streets and quiet corners.", "Free", 4.8, "https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=700&q=85", "Alfama, Lisbon", 38.7112, -9.1300, "Open route window", 85, "walking,views,local"),
                new Place("Chiado Coffee Room", "Lisbon", PlaceCategory.COFFEE, "A calm cafe window near bookstores, shops and gentle walking streets.", "Lean", 4.6, "https://images.unsplash.com/photo-1511920170033-f8396924c348?auto=format&fit=crop&w=700&q=85", "Chiado, Lisbon", 38.7107, -9.1439, "08:30 - 19:00", 45, "coffee,books,break"),
                new Place("Time Out Food Hall", "Lisbon", PlaceCategory.FOOD, "An easy local-food decision point when travelers want choice without a formal booking.", "Mid", 4.5, "https://images.unsplash.com/photo-1551218808-94e220e084d2?auto=format&fit=crop&w=700&q=85", "Cais do Sodre, Lisbon", 38.7068, -9.1456, "10:00 - 23:00", 80, "food,market,flexible"),
                new Place("MAAT Culture Edge", "Lisbon", PlaceCategory.CULTURE, "A compact riverfront culture stop that pairs well with sunset walking.", "Mid", 4.6, "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=700&q=85", "Belem, Lisbon", 38.6958, -9.2092, "10:00 - 19:00", 110, "culture,river,design"),

                new Place("Old Town Morning Loop", "Prague", PlaceCategory.WALKING, "A compact walk through historic streets before the central areas get crowded.", "Free", 4.7, "https://images.unsplash.com/photo-1519677100203-a0e668c92439?auto=format&fit=crop&w=700&q=85", "Old Town, Prague", 50.0870, 14.4208, "Open route window", 80, "walking,history,free"),
                new Place("Kampa Gallery Stop", "Prague", PlaceCategory.CULTURE, "A manageable culture stop near the river with easy walking connections.", "Lean", 4.6, "https://images.unsplash.com/photo-1541849546-216549ae216d?auto=format&fit=crop&w=700&q=85", "Kampa, Prague", 50.0843, 14.4072, "10:00 - 18:00", 95, "culture,river,compact"),
                new Place("Vinohrady Coffee Break", "Prague", PlaceCategory.COFFEE, "A quieter cafe break in a neighborhood that feels lived-in rather than rushed.", "Lean", 4.7, "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=700&q=85", "Vinohrady, Prague", 50.0755, 14.4419, "08:00 - 18:00", 45, "coffee,local,break"),
                new Place("Lokal Dinner Window", "Prague", PlaceCategory.FOOD, "A reliable local dinner zone that keeps the evening practical and low-stress.", "Mid", 4.6, "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=700&q=85", "Old Town, Prague", 50.0902, 14.4248, "12:00 - 22:30", 90, "food,local,dinner"),

                new Place("MuseumsQuartier Window", "Vienna", PlaceCategory.CULTURE, "A premium-feeling culture anchor with cafes and flexible indoor backup nearby.", "Mid", 4.8, "https://images.unsplash.com/photo-1516550893923-42d28e5677af?auto=format&fit=crop&w=700&q=85", "MuseumsQuartier, Vienna", 48.2034, 16.3597, "10:00 - 18:00", 120, "culture,museums,indoor"),
                new Place("Naschmarkt Food Route", "Vienna", PlaceCategory.FOOD, "A market-led food stop with broad options and short route friction.", "Mid", 4.6, "https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?auto=format&fit=crop&w=700&q=85", "Naschmarkt, Vienna", 48.1990, 16.3640, "09:00 - 18:30", 75, "food,market,local"),
                new Place("Cafe Central Pause", "Vienna", PlaceCategory.COFFEE, "A classic coffee window that fits culture-heavy days without feeling random.", "Comfort", 4.7, "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85", "Innere Stadt, Vienna", 48.2102, 16.3654, "08:00 - 21:00", 55, "coffee,classic,central"),
                new Place("Ringstrasse Easy Walk", "Vienna", PlaceCategory.FREE, "A low-cost architectural walk that gives structure to the afternoon.", "Free", 4.6, "https://images.unsplash.com/photo-1573599852326-2bcc1299b0ff?auto=format&fit=crop&w=700&q=85", "Ringstrasse, Vienna", 48.2082, 16.3738, "Open route window", 80, "free,walking,architecture")
        ).forEach(this::upsertPlace);
    }

    private void upsertPlace(Place seed) {
        Place place = placeRepository.findByNameIgnoreCaseAndCityIgnoreCase(seed.getName(), seed.getCity()).orElse(seed);
        place.setCategory(seed.getCategory());
        place.setDescription(seed.getDescription());
        place.setPriceLevel(seed.getPriceLevel());
        place.setRating(seed.getRating());
        place.setImageUrl(seed.getImageUrl());
        place.setAddress(seed.getAddress());
        place.setLatitude(seed.getLatitude());
        place.setLongitude(seed.getLongitude());
        place.setOpeningHours(seed.getOpeningHours());
        place.setEstimatedVisitMinutes(seed.getEstimatedVisitMinutes());
        place.setTags(seed.getTags());
        placeRepository.save(place);
    }

    private void seedNotifications(UserAccount user) {
        appNotificationRepository.saveAll(List.of(
                new AppNotification(user, "Route", "Route adjusted", "Your afternoon can be lighter by moving the canal walk before lunch.", true, Instant.now().minusSeconds(1800)),
                new AppNotification(user, "Food", "Dinner window", "A local dinner area is available near your last stop.", true, Instant.now().minusSeconds(5400)),
                new AppNotification(user, "Weather", "Rain backup ready", "Indoor alternatives are ready if the forecast changes.", false, Instant.now().minusSeconds(90_000))
        ));
    }
}
