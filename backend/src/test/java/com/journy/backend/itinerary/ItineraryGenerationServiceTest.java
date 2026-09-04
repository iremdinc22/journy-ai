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

@SpringBootTest(properties = {"journy.places.osm.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:phase10_generation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
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

    @org.springframework.test.context.transaction.BeforeTransaction
    void committedVerifiedCache() {
        for (int i = 0; i < 12; i++) {
            placeRepository.save(com.journy.backend.support.VerifiedPlaceFixtures.place(
                    "gen_" + i, "Verified " + i, "Amsterdam", PlaceCategory.CULTURE));
        }
    }

    @Test
    @Transactional
    void generateIfMissingCreatesDaysBasedOnTripLengthAndPlaces() {
        UserAccount user = userAccountRepository.save(new UserAccount(
                "Plan User",
                "plan-" + System.nanoTime() + "@journy.app",
                passwordEncoder.encode("secret"),
                "Balanced traveler"
        ));
        Trip trip = tripRepository.save(new Trip(
                user,
                "Amsterdam",
                "Centraal Station",
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
        assertThat(trip.getTotalStops()).isEqualTo(12);
        assertThat(days.stream().flatMap(day -> day.getStops().stream())).allSatisfy(stop -> assertThat(stop.getSource()).isEqualTo("provider:osm"));
        assertThat(trip.getAverageWalkKm()).isGreaterThan(0);
    }
}
