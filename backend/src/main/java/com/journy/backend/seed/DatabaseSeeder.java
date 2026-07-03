package com.journy.backend.seed;

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
    private final AppNotificationRepository appNotificationRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(
            UserAccountRepository userAccountRepository,
            TripRepository tripRepository,
            ItineraryDayRepository itineraryDayRepository,
            PlaceRepository placeRepository,
            AppNotificationRepository appNotificationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tripRepository = tripRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.placeRepository = placeRepository;
        this.appNotificationRepository = appNotificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
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
        seedPlaces();
        seedNotifications(user);
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
        placeRepository.saveAll(List.of(
                new Place("Local Bistro Noord", "Amsterdam", PlaceCategory.FOOD, "Seasonal plates in a calm neighborhood room.", "Mid", 4.7, ""),
                new Place("Canal Bakery", "Amsterdam", PlaceCategory.FOOD, "Morning pastries and simple coffee near the route.", "Lean", 4.6, ""),
                new Place("Museumplein Window", "Amsterdam", PlaceCategory.CULTURE, "A compact culture stop that keeps the day balanced.", "Mid", 4.8, ""),
                new Place("Small Gallery Walk", "Amsterdam", PlaceCategory.CULTURE, "Independent galleries close to cafe streets.", "Lean", 4.5, ""),
                new Place("Quiet Cup De Pijp", "Amsterdam", PlaceCategory.COFFEE, "A soft-paced cafe for planning the next stop.", "Lean", 4.7, ""),
                new Place("Canal Roast Bar", "Amsterdam", PlaceCategory.COFFEE, "Good espresso without a long detour.", "Mid", 4.6, ""),
                new Place("Golden Hour Canal Loop", "Amsterdam", PlaceCategory.FREE, "A scenic walking loop with no reservation needed.", "Free", 4.8, ""),
                new Place("Neighborhood Market", "Amsterdam", PlaceCategory.FREE, "Low-cost local browsing and quick bites.", "Free", 4.5, "")
        ));
    }

    private void seedNotifications(UserAccount user) {
        appNotificationRepository.saveAll(List.of(
                new AppNotification(user, "Route", "Route adjusted", "Your afternoon can be lighter by moving the canal walk before lunch.", true, Instant.now().minusSeconds(1800)),
                new AppNotification(user, "Food", "Dinner window", "A local dinner area is available near your last stop.", true, Instant.now().minusSeconds(5400)),
                new AppNotification(user, "Weather", "Rain backup ready", "Indoor alternatives are ready if the forecast changes.", false, Instant.now().minusSeconds(90_000))
        ));
    }
}
