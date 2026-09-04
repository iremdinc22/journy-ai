package com.journy.backend.feedback;

import com.journy.backend.feedback.dto.TasteFeedbackRequest;
import com.journy.backend.feedback.model.*;
import com.journy.backend.feedback.repository.TasteFeedbackRepository;
import com.journy.backend.feedback.service.TasteFeedbackService;
import com.journy.backend.savedplace.service.SavedPlaceService;
import com.journy.backend.savedplace.dto.SavedPlaceRequest;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.dto.*;
import com.journy.backend.itinerary.model.*;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryService;
import com.journy.backend.trip.enums.*;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.support.VerifiedPlaceFixtures;
import com.journy.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.journy.backend.feedback.model.TasteFeedbackAction.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"journy.places.osm.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:personalization_events;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PersonalizationEventIntegrationTest {
    @Autowired TasteFeedbackService feedback;
    @Autowired TasteFeedbackRepository events;
    @Autowired SavedPlaceService favorites;
    @Autowired ItineraryService itineraries;
    @Autowired ItineraryDayRepository days;
    @Autowired TripRepository trips;
    @Autowired PlaceRepository places;
    @Autowired UserAccountRepository users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UserAccount user;
    Place place;
    Trip trip;
    ItineraryStop stop;

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        user = users.save(new UserAccount("Events", suffix + "@example.test", "unused", "Balanced traveler"));
        authenticate(user);
        place = places.save(VerifiedPlaceFixtures.place("osm_" + suffix, "Canonical coffee", "London", PlaceCategory.COFFEE));
        var date = LocalDate.now().plusDays(2);
        trip = trips.save(new Trip(user, "London", "", date, date, TravelerType.SOLO,
                BudgetMode.BALANCED, TripPace.BALANCED, Set.of(TravelInterest.WALKING)));
        var day = new ItineraryDay(trip, 1, "Day", "Summary", 1);
        stop = new ItineraryStop(1, place.getName(), place.getCategory().name(), "10:00", place.getId(),
                "provider:osm", "Visit", place.getLatitude(), place.getLongitude());
        day.addStop(stop);
        days.saveAndFlush(day);
    }
    @AfterEach void clearAuth() { SecurityContextHolder.clearContext(); }
    void authenticate(UserAccount account) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account.getEmail(), null, List.of()));
    }
    List<TasteFeedback> history() { return events.findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail()); }
    SavedPlaceRequest saveRequest(String id) {
        return new SavedPlaceRequest(id, "Tampered name", "London", "CULTURE", "Description", "Mid",
                4.5, "https://example.test/image", null, null, null, null);
    }
    TasteFeedbackRequest negative(String id, TasteFeedbackAction action) {
        return new TasteFeedbackRequest(id, "Tampered name", "CULTURE", action, "Not for me");
    }
    AddPlaceToPlanRequest addRequest(String id) {
        return new AddPlaceToPlanRequest(id, "Tampered", "London", "WALKING", "Description", "Mid",
                4.5, null, null, null, null, null, null);
    }
    void changeStatus(StopVisitStatus value) {
        itineraries.updateStopStatus(trip.getId(), 1, stop.getId(), new UpdateStopStatusRequest(value));
    }
    void assertCanonical(TasteFeedbackAction action) {
        assertThat(history()).hasSize(1);
        var event = history().getFirst();
        assertThat(event.getAction()).isEqualTo(action);
        assertThat(event.getPlaceId()).isEqualTo(place.getId()).isNotEqualTo(stop.getId());
        assertThat(event.getPlaceName()).isEqualTo("Canonical coffee");
        assertThat(event.getCategory()).isEqualTo("COFFEE");
        assertThat(event.getIdentityVerified()).isTrue();
        assertThat(event.getEventKey()).isNotBlank();
    }

    @Test void saveRetryProducesOneCanonicalEvent() {
        favorites.save(saveRequest(place.getId()));
        favorites.save(saveRequest(place.getId()));
        assertCanonical(SAVED);
        assertThat(favorites.list()).hasSize(1);
    }
    @Test void unsaveRetryAndNewSaveCycle() {
        favorites.save(saveRequest(place.getId()));
        favorites.remove(place.getId());
        favorites.remove(place.getId());
        assertThat(history()).extracting(TasteFeedback::getAction).containsExactlyInAnyOrder(SAVED, UNSAVED);
        assertThat(favorites.list()).isEmpty();
        favorites.save(saveRequest(place.getId()));
        assertThat(history()).filteredOn(e -> e.getAction() == SAVED).hasSize(2);
    }
    @Test void doneRetryAndStatusResetCannotMultiplyVisit() {
        changeStatus(StopVisitStatus.DONE);
        changeStatus(StopVisitStatus.DONE);
        changeStatus(StopVisitStatus.PLANNED);
        changeStatus(StopVisitStatus.DONE);
        assertCanonical(VISITED);
    }
    @Test void skipRetryDoesNotMultiplyEvent() {
        changeStatus(StopVisitStatus.SKIPPED);
        changeStatus(StopVisitStatus.SKIPPED);
        assertCanonical(SKIPPED);
    }
    @Test void removeUsesCanonicalIdAndRetryDoesNotRecord() {
        itineraries.removeStop(trip.getId(), 1, stop.getId());
        assertThatThrownBy(() -> itineraries.removeStop(trip.getId(), 1, stop.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertCanonical(REMOVED_FROM_TRIP);
    }
    @Test void addOnlyRecordsSuccessfulNewStop() {
        var added = places.save(VerifiedPlaceFixtures.place("osm_" + UUID.randomUUID(), "Museum", "London", PlaceCategory.CULTURE));
        itineraries.addPlaceToDay(trip.getId(), 1, addRequest(added.getId()));
        itineraries.addPlaceToDay(trip.getId(), 1, addRequest(added.getId()));
        assertThat(history()).hasSize(1);
        assertThat(history().getFirst().getAction()).isEqualTo(ADDED_TO_TRIP);
        assertThat(history().getFirst().getPlaceId()).isEqualTo(added.getId());
        assertThat(history().getFirst().getWeight()).isZero();
        assertThatThrownBy(() -> itineraries.addPlaceToDay(trip.getId(), 1, addRequest("fabricated")))
                .isInstanceOf(com.journy.backend.common.exception.InsufficientDestinationDataException.class);
        assertThat(history()).hasSize(1);
    }
    @Test void explicitFeedbackCanonicalizesAndDeduplicates() {
        var first = feedback.record(negative(place.getId(), NOT_INTERESTED));
        var retry = feedback.record(negative(place.getId(), NOT_INTERESTED));
        assertThat(retry.id()).isEqualTo(first.id());
        assertCanonical(NOT_INTERESTED);
    }
    @Test void allExistingExplicitWeightsRemainUnchanged() {
        for (var action : List.of(NOT_INTERESTED, TOO_EXPENSIVE, TOO_FAR, ALREADY_VISITED))
            feedback.record(negative(place.getId(), action));
        assertThat(history()).extracting(TasteFeedback::getWeight).containsExactlyInAnyOrder(-3, -2, -2, 4);
    }
    @Test void unverifiedIdentitiesDoNotBecomeEvidence() {
        for (String id : List.of("fabricated", stop.getId(), "preview_123")) {
            assertThatThrownBy(() -> feedback.record(negative(id, NOT_INTERESTED)))
                    .isInstanceOf(ResourceNotFoundException.class);
            favorites.save(saveRequest(id));
            favorites.remove(id);
        }
        for (String provider : List.of("starter", "seed", "planned_fallback")) {
            place.setProvider(provider);
            places.saveAndFlush(place);
            assertThatThrownBy(() -> feedback.record(negative(place.getId(), NOT_INTERESTED)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
        assertThat(history()).isEmpty();
    }
    @Test void historicalNullIdentityStillSupportsStatusAndRemoval() {
        legacyStopIdentity();
        changeStatus(StopVisitStatus.DONE);
        changeStatus(StopVisitStatus.SKIPPED);
        itineraries.removeStop(trip.getId(), 1, stop.getId());
        assertThat(history()).isEmpty();
    }
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired jakarta.persistence.EntityManager entityManager;
    void legacyStopIdentity() {
        transactions.executeWithoutResult(tx -> entityManager.find(ItineraryStop.class, stop.getId()).setPlaceId(null));
    }
    @Test void usersAreIsolatedAndCannotChangeOthersTrip() {
        feedback.record(negative(place.getId(), NOT_INTERESTED));
        var other = users.save(new UserAccount("Other", UUID.randomUUID() + "@example.test", "unused", "Balanced traveler"));
        authenticate(other);
        assertThat(events.findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(other.getEmail())).isEmpty();
        assertThatThrownBy(() -> changeStatus(StopVisitStatus.DONE)).isInstanceOf(ResourceNotFoundException.class);
        feedback.record(negative(place.getId(), NOT_INTERESTED));
        assertThat(events.findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(other.getEmail())).hasSize(1);
        assertCanonical(NOT_INTERESTED);
    }
    @Test void concurrentDoubleSaveProducesOneFavoriteAndEvent() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> save = () -> {
                authenticate(user);
                try { start.await(); favorites.save(saveRequest(place.getId())); return null; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(save);
            var second = executor.submit(save);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertCanonical(SAVED);
        assertThat(favorites.list()).hasSize(1);
    }
    @Test void rolledBackFavoriteDoesNotLeaveAnEvent() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            favorites.save(saveRequest(place.getId()));
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(favorites.list()).isEmpty();
        assertThat(history()).isEmpty();
    }
    @Test void historicalRowsRemainReadableAndAreNotUpgraded() {
        var old = events.saveAndFlush(new TasteFeedback(user, stop.getId(), "Legacy", "WALKING",
                REMOVED, -2, "Historical ambiguous identity"));
        feedback.record(negative(place.getId(), NOT_INTERESTED));
        var loaded = events.findById(old.getId()).orElseThrow();
        assertThat(loaded.getPlaceId()).isEqualTo(stop.getId());
        assertThat(loaded.getAction()).isEqualTo(REMOVED);
        assertThat(loaded.getIdentityVerified()).isNull();
        assertThat(loaded.getEventKey()).isNull();
        assertThat(history()).hasSize(2);
    }

    @Test void publicApiPreservesNegativeContractAndRejectsForgedStateEvents() throws Exception {
        mvc.perform(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(negative(place.getId(), NOT_INTERESTED))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("COFFEE"));
        mvc.perform(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(negative(place.getId(), SAVED))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(negative("fake", NOT_INTERESTED))))
                .andExpect(status().isNotFound());
        assertCanonical(NOT_INTERESTED);
    }
    @Test void savedApiRemainsCompatibleWithRepeatedRequests() throws Exception {
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/saved-places").contentType("application/json")
                    .content(json.writeValueAsString(saveRequest(place.getId())))).andExpect(status().isOk());
        for (int i = 0; i < 2; i++)
            mvc.perform(delete("/api/saved-places/" + place.getId())).andExpect(status().isOk());
        assertThat(history()).extracting(TasteFeedback::getAction).containsExactlyInAnyOrder(SAVED, UNSAVED);
    }
}
