package com.journy.backend.itinerary;

import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryGenerationService;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ItineraryGenerationServiceTest {
    @Autowired
    private ItineraryGenerationService itineraryGenerationService;
    @Autowired
    private ItineraryDayRepository itineraryDayRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void generateIfMissingCreatesDaysBasedOnTripLengthAndPlaces() {
        UserAccount user = userAccountRepository.save(new UserAccount(
                "Plan User",
                "plan-" + System.nanoTime() + "@journy.app",
                passwordEncoder.encode("secret"),
                "Balanced traveler"
        ));
        placeRepository.saveAll(List.of(
                new Place("Culture Place", "Amsterdam", PlaceCategory.CULTURE, "Culture stop", "Lean", 4.7, ""),
                new Place("Food Place", "Amsterdam", PlaceCategory.FOOD, "Food stop", "Mid", 4.8, ""),
                new Place("Coffee Place", "Amsterdam", PlaceCategory.COFFEE, "Coffee stop", "Lean", 4.6, "")
        ));
        Trip trip = tripRepository.save(new Trip(
                user,
                "Amsterdam",
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 13),
                TravelerType.COUPLE,
                BudgetMode.BALANCED,
                TripPace.BALANCED,
                Set.of(TravelInterest.COFFEE, TravelInterest.LOCAL_FOOD, TravelInterest.MUSEUMS)
        ));

        itineraryGenerationService.generateIfMissing(trip);

        List<ItineraryDay> days = itineraryDayRepository.findByTripIdOrderByDayNumberAsc(trip.getId());
        assertThat(days).hasSize(3);
        assertThat(days.getFirst().getStops()).isNotEmpty();
        assertThat(trip.getTotalStops()).isGreaterThan(0);
        assertThat(trip.getAverageWalkKm()).isGreaterThan(0);
    }
}
