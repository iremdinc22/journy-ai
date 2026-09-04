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
        "spring.datasource.url=jdbc:h2:mem:personalization_adversarial;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalizationAdversarialIntegrationTest {
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


    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.journy.backend.security.JwtService jwt;
    @Autowired com.journy.backend.profile.service.ProfileService profiles;
    @Autowired com.journy.backend.explore.service.ExploreService explore;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    List<TasteFeedback> history(UserAccount owner) {
        return events.findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(owner.getEmail());
    }
    String stopsUrl() { return "/api/trips/" + trip.getId() + "/itinerary/days/1/stops"; }
    org.springframework.test.web.servlet.ResultActions api(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        SecurityContextHolder.clearContext(); // Exercise JWT filter rather than a pre-authenticated test context.
        try { return mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(user))); }
        finally { authenticate(user); }
    }
    void race(Runnable first, Runnable second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<?>>();
            for (Runnable operation : List.of(first, second)) tasks.add(executor.submit(() -> {
                authenticate(user);
                try {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                    operation.run();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
    }
    void invariant(TasteFeedback event, String source, String context, TasteFeedbackAction action) throws Exception {
        assertThat(event.getUser().getId()).isEqualTo(user.getId());
        assertThat(event.getPlaceId()).isEqualTo(place.getId()).isNotEqualTo(stop.getId());
        assertThat(event.getPlaceName()).isEqualTo(place.getName());
        assertThat(event.getCategory()).isEqualTo(place.getCategory().name());
        assertThat(event.getIdentityVerified()).isTrue();
        assertThat(event.getSource()).isEqualTo(source);
        assertThat(event.getContextId()).isEqualTo(context).isNotBlank();
        assertThat(event.getAction()).isEqualTo(action);
        String raw = source.length() + ":" + source + context.length() + ":" + context + ":" + action.name();
        String expected = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(event.getEventKey()).isEqualTo(expected);
    }

    @Test void fullFavoriteLifecycleHasNewContextsAndValidInvariants() throws Exception {
        var saved = favorites.save(saveRequest(place.getId()));
        favorites.save(saveRequest(place.getId()));
        favorites.remove(place.getId());
        favorites.remove(place.getId());
        var again = favorites.save(saveRequest(place.getId()));
        assertThat(again.id()).isNotEqualTo(saved.id());
        assertThat(history()).hasSize(3);
        for (var event : history())
            invariant(event, "SAVED_PLACE", event.getAction() == UNSAVED ? saved.id() : event.getContextId(), event.getAction());
        assertThat(history()).filteredOn(e -> e.getAction() == SAVED)
                .extracting(TasteFeedback::getContextId).containsExactlyInAnyOrder(saved.id(), again.id());
        assertThat(history()).extracting(TasteFeedback::getEventKey).doesNotHaveDuplicates();
    }

    @Test void concurrentRemovalsCommitOneUnsave() throws Exception {
        favorites.save(saveRequest(place.getId()));
        race(() -> favorites.remove(place.getId()), () -> favorites.remove(place.getId()));
        assertThat(favorites.list()).isEmpty();
        assertThat(history()).extracting(TasteFeedback::getAction).containsExactlyInAnyOrder(SAVED, UNSAVED);
    }
    @Test void saveRemoveRaceHistoryMatchesCommittedState() throws Exception {
        for (int i = 0; i < 6; i++) {
            favorites.remove(place.getId());
            long before = history().size();
            race(() -> favorites.save(saveRequest(place.getId())), () -> favorites.remove(place.getId()));
            var added = history().stream().limit(history().size() - before).toList();
            assertThat(added).filteredOn(e -> e.getAction() == SAVED).hasSize(1);
            assertThat(added).filteredOn(e -> e.getAction() == UNSAVED).hasSize(favorites.list().isEmpty() ? 1 : 0);
            assertThat(history()).extracting(TasteFeedback::getEventKey).doesNotHaveDuplicates();
        }
    }
    @Test void concurrentExplicitFeedbackReturnsOneEvent() throws Exception {
        race(() -> feedback.record(negative(place.getId(), NOT_INTERESTED)),
                () -> feedback.record(negative(place.getId(), NOT_INTERESTED)));
        assertCanonical(NOT_INTERESTED);
    }
    @Test void concurrentDoneTransitionsEmitOneVisit() throws Exception {
        race(() -> changeStatus(StopVisitStatus.DONE), () -> changeStatus(StopVisitStatus.DONE));
        assertCanonical(VISITED);
    }
    @Test void existingStatusMachineAllowsBothTerminalActionsButCountsEachOnce() throws Exception {
        for (var state : List.of(StopVisitStatus.SKIPPED, StopVisitStatus.SKIPPED, StopVisitStatus.PLANNED,
                StopVisitStatus.SKIPPED, StopVisitStatus.DONE, StopVisitStatus.SKIPPED, StopVisitStatus.DONE)) {
            changeStatus(state);
            transactions.executeWithoutResult(tx ->
                    assertThat(entityManager.find(ItineraryStop.class, stop.getId()).getStatus()).isEqualTo(state));
        }
        assertThat(history()).extracting(TasteFeedback::getAction).containsExactlyInAnyOrder(VISITED, SKIPPED);
        for (var event : history()) invariant(event, "ITINERARY", stop.getId(), event.getAction());
    }
    @Test void sameStatusRetryPreservesCompletionTimestamp() {
        changeStatus(StopVisitStatus.DONE);
        var before = transactions.execute(tx -> entityManager.find(ItineraryStop.class, stop.getId()).getCompletedAt());
        changeStatus(StopVisitStatus.DONE);
        var after = transactions.execute(tx -> entityManager.find(ItineraryStop.class, stop.getId()).getCompletedAt());
        assertThat(after).isEqualTo(before);
    }
    @Test void explicitAddAndRemoveUsePlaceIdentityAndStopContext() throws Exception {
        itineraries.removeStop(trip.getId(), 1, stop.getId());
        invariant(history().getFirst(), "ITINERARY", stop.getId(), REMOVED_FROM_TRIP);
        var result = itineraries.addPlaceToDay(trip.getId(), 1, addRequest(place.getId()));
        var added = result.stops().getFirst();
        assertThat(added.id()).isNotEqualTo(place.getId()).isNotEqualTo(stop.getId());
        invariant(history().getFirst(), "ITINERARY", added.id(), ADDED_TO_TRIP);
    }
    @Test void historicalUnknownAndNullIdentitiesDoNotEmitEvents() {
        for (String identity : Arrays.asList(null, "historical-preview")) {
            transactions.executeWithoutResult(tx -> entityManager.find(ItineraryStop.class, stop.getId()).setPlaceId(identity));
            changeStatus(StopVisitStatus.PLANNED);
            changeStatus(StopVisitStatus.DONE);
            changeStatus(StopVisitStatus.SKIPPED);
        }
        itineraries.removeStop(trip.getId(), 1, stop.getId());
        assertThat(history()).isEmpty();
    }
    @Test void incompleteOrInvalidCanonicalRecordsAreRejected() {
        List<java.util.function.Consumer<Place>> corruptions = List.of(
                p -> p.setProviderFetchedAt(null), p -> p.setProvider(null),
                p -> p.setProvider("invalid/provider"), p -> p.setProvider("STARTER"),
                p -> p.setProvider("planned_fallback"), p -> p.setLatitude(null),
                p -> p.setLatitude(91.0), p -> p.setLongitude(-181.0),
                p -> { p.setLatitude(0.0); p.setLongitude(0.0); }, p -> p.setName(" "),
                p -> p.setCity(" "));
        for (var corrupt : corruptions) {
            var invalid = VerifiedPlaceFixtures.place("invalid_" + UUID.randomUUID(), "Invalid", "London", PlaceCategory.COFFEE);
            corrupt.accept(invalid);
            places.saveAndFlush(invalid);
            assertThatThrownBy(() -> feedback.record(negative(invalid.getId(), NOT_INTERESTED)))
                    .isInstanceOf(ResourceNotFoundException.class);
            favorites.save(saveRequest(invalid.getId()));
            favorites.remove(invalid.getId());
        }
        assertThat(history()).isEmpty();
    }
    @Test void nullGarbageAndCaseChangedMetadataCannotOverrideCanonicalData() throws Exception {
        for (String metadata : Arrays.asList(null, "!!!garbage###", "cOfFeE")) {
            feedback.record(new TasteFeedbackRequest(place.getId(), metadata, metadata, NOT_INTERESTED, metadata));
            assertCanonical(NOT_INTERESTED);
        }
        api(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(new TasteFeedbackRequest(place.getId(), null, null, TOO_FAR, null))))
                .andExpect(status().isBadRequest()); // Existing @NotBlank API contract.
        assertThat(history()).hasSize(1);
    }
    @Test void everyExplicitActionDeduplicatesIndependentOfReason() throws Exception {
        for (var action : List.of(NOT_INTERESTED, TOO_EXPENSIVE, TOO_FAR, ALREADY_VISITED)) {
            var first = feedback.record(negative(place.getId(), action));
            var again = feedback.record(new TasteFeedbackRequest(place.getId(), "x", "food", action, "changed reason"));
            assertThat(again.id()).isEqualTo(first.id());
        }
        assertThat(history()).hasSize(4);
        assertThat(history()).extracting(TasteFeedback::getWeight).containsExactlyInAnyOrder(-3, -2, -2, 4);
        for (var event : history()) invariant(event, "EXPLORE", place.getId(), event.getAction());
        assertThat(history()).extracting(TasteFeedback::getEventKey).doesNotHaveDuplicates();
    }
    @Test void ownershipAndSameMaterialKeysArePerUser() throws Exception {
        var favoritePlace = places.save(VerifiedPlaceFixtures.place("favorite_" + UUID.randomUUID(), "Favorite", "London", PlaceCategory.CULTURE));
        var negativePlace = places.save(VerifiedPlaceFixtures.place("negative_" + UUID.randomUUID(), "Negative", "London", PlaceCategory.COFFEE));
        favorites.save(saveRequest(favoritePlace.getId()));
        changeStatus(StopVisitStatus.DONE);
        feedback.record(negative(negativePlace.getId(), NOT_INTERESTED));
        var other = users.save(new UserAccount("B", UUID.randomUUID() + "@example.test", "unused", "Balanced traveler"));
        authenticate(other);
        assertThat(history(other)).isEmpty();
        favorites.remove(favoritePlace.getId());
        assertThat(history(other)).isEmpty();
        assertThatThrownBy(() -> changeStatus(StopVisitStatus.DONE)).isInstanceOf(ResourceNotFoundException.class);
        favorites.save(saveRequest(favoritePlace.getId()));
        feedback.record(negative(negativePlace.getId(), NOT_INTERESTED));
        assertThat(history(other)).hasSize(2);
        var aKey = history().stream().filter(e -> e.getAction() == NOT_INTERESTED).findFirst().orElseThrow().getEventKey();
        var bKey = history(other).stream().filter(e -> e.getAction() == NOT_INTERESTED).findFirst().orElseThrow().getEventKey();
        assertThat(bKey).isEqualTo(aKey);
        authenticate(user);
        assertThat(favorites.list()).hasSize(1);
        assertThat(history()).hasSize(3);
        var payload = json.valueToTree(negative(negativePlace.getId(), TOO_FAR));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("userId", other.getId());
        api(post("/api/taste-feedback").contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isOk());
        assertThat(history(other)).hasSize(2);
        assertThat(history()).hasSize(4);
    }
    @Test void contextSeparatorsAndPrefixesHaveDistinctDeterministicKeys() throws Exception {
        for (String id : List.of("a:b", "ab:", "a::b", ":ab", "a", "aa", "é:☕")) {
            var canonical = places.save(VerifiedPlaceFixtures.place(id, "Canonical", "London", PlaceCategory.COFFEE));
            var first = feedback.record(negative(canonical.getId(), TOO_FAR));
            assertThat(feedback.record(negative(canonical.getId(), TOO_FAR)).id()).isEqualTo(first.id());
        }
        assertThat(history()).hasSize(7);
        assertThat(history()).extracting(TasteFeedback::getEventKey).doesNotHaveDuplicates();
        assertThatThrownBy(() -> feedback.record(negative("", TOO_FAR))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void databaseRejectsDuplicateKeyButAllowsHistoricalNulls() {
        feedback.record(negative(place.getId(), NOT_INTERESTED));
        var existing = history().getFirst();
        var duplicate = new TasteFeedback(user, place.getId(), place.getName(), "COFFEE", NOT_INTERESTED, -3, null);
        duplicate.setEventKey(existing.getEventKey());
        assertThatThrownBy(() -> events.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for (var action : List.of(REMOVED, REPLACED)) {
            events.saveAndFlush(new TasteFeedback(user, stop.getId(), "Historical", "COFFEE", action, -2, null));
        }
        assertThat(history()).hasSize(3);
    }
    @Test void feedbackConstraintFailureRollsBackFavoriteAndItineraryState() {
        // Test-only constraint exercises a real database write failure, not a mocked service failure.
        jdbc.execute("alter table taste_feedback add constraint phase3_reject_user check (user_id <> '" + user.getId() + "')");
        try {
            assertThatThrownBy(() -> favorites.save(saveRequest(place.getId())))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(favorites.list()).isEmpty();
            assertThatThrownBy(() -> changeStatus(StopVisitStatus.DONE))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            transactions.executeWithoutResult(tx ->
                    assertThat(entityManager.find(ItineraryStop.class, stop.getId()).getStatus()).isEqualTo(StopVisitStatus.PLANNED));
            assertThat(history()).isEmpty();
        } finally { jdbc.execute("alter table taste_feedback drop constraint phase3_reject_user"); }
    }
    @Test void failureAfterAttemptedItineraryEventRollsBackRemoval() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            itineraries.removeStop(trip.getId(), 1, stop.getId());
            entityManager.flush();
            throw new IllegalStateException("Failure after flush before commit");
        })).isInstanceOf(IllegalStateException.class);
        transactions.executeWithoutResult(tx -> assertThat(entityManager.find(ItineraryStop.class, stop.getId())).isNotNull());
        assertThat(history()).isEmpty();
    }
    @Test void legacyRowsSurviveProfileAndExploreReadsUnchanged() {
        for (var action : List.of(REMOVED, REPLACED))
            events.saveAndFlush(new TasteFeedback(user, null, "Historical", "COFFEE", action, -2, null));
        assertThat(profiles.me()).isNotNull();
        assertThat(explore.places("For you", null)).isNotNull();
        assertThat(history()).hasSize(2);
        for (var event : history()) {
            assertThat(event.getIdentityVerified()).isNull();
            assertThat(event.getSource()).isNull();
            assertThat(event.getContextId()).isNull();
            assertThat(event.getEventKey()).isNull();
            assertThat(event.getPlaceId()).isNull();
        }
    }
    @Test void neutralAddsDisplaceOlderEvidenceInLatest80Window() {
        feedback.record(negative(place.getId(), NOT_INTERESTED));
        var oldId = history().getFirst().getId();
        transactions.executeWithoutResult(tx -> {
            entityManager.find(TasteFeedback.class, oldId).setCreatedAt(java.time.Instant.now().minusSeconds(3600));
            for (int i = 0; i < 80; i++) {
                var added = places.save(VerifiedPlaceFixtures.place("window_" + UUID.randomUUID(), "Place " + i, "London", PlaceCategory.CULTURE));
                var date = LocalDate.now().plusDays(i + 2);
                var anotherTrip = trips.save(new Trip(user, "London", "", date, date, TravelerType.SOLO,
                        BudgetMode.BALANCED, TripPace.BALANCED, Set.of(TravelInterest.WALKING)));
                days.save(new ItineraryDay(anotherTrip, 1, "Day", "Summary", 0));
                itineraries.addPlaceToDay(anotherTrip.getId(), 1, addRequest(added.getId()));
            }
        });
        assertThat(history()).hasSize(80).allMatch(e -> e.getAction() == ADDED_TO_TRIP && e.getWeight() == 0);
        assertThat(events.findById(oldId)).isPresent();
    }
    @Test void summaryConstraintFailureRollsBackAttemptedAddAndEvent() {
        transactions.executeWithoutResult(tx -> {
            var day = days.findByTripIdOrderByDayNumberAsc(trip.getId()).getFirst();
            day.setSummary("S".repeat(500));
        });
        var extra = places.save(VerifiedPlaceFixtures.place("extra_" + UUID.randomUUID(), "Extra", "London", PlaceCategory.CULTURE));
        assertThatThrownBy(() -> itineraries.addPlaceToDay(trip.getId(), 1, addRequest(extra.getId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        transactions.executeWithoutResult(tx -> {
            var day = days.findByTripIdOrderByDayNumberAsc(trip.getId()).getFirst();
            assertThat(day.getStops()).hasSize(1);
            assertThat(day.getSummary()).hasSize(500);
        });
        assertThat(history()).isEmpty();
    }
    @Test void feedbackFailureDuringUnsavePreservesFavorite() {
        favorites.save(saveRequest(place.getId()));
        jdbc.execute("alter table taste_feedback add constraint phase3_reject_unsave check (action <> 'UNSAVED' or user_id <> '" + user.getId() + "')");
        try {
            assertThatThrownBy(() -> favorites.remove(place.getId()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(favorites.list()).hasSize(1);
            assertCanonical(SAVED);
        } finally { jdbc.execute("alter table taste_feedback drop constraint phase3_reject_unsave"); }
    }

    @Test void authenticatedApiValidatesTransitionsOwnershipAndMalformedPayloads() throws Exception {
        api(patch(stopsUrl() + "/" + stop.getId() + "/status").contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());
        api(patch(stopsUrl() + "/" + stop.getId() + "/status").contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());
        assertCanonical(VISITED);
        api(patch(stopsUrl() + "/" + stop.getId() + "/status").contentType("application/json").content("{\"status\":\"BAD\"}"))
                .andExpect(status().isBadRequest());
        api(post("/api/saved-places").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        api(post("/api/saved-places").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        api(post(stopsUrl()).contentType("application/json").content(json.writeValueAsString(addRequest("fake"))))
                .andExpect(status().isUnprocessableEntity());
        api(delete(stopsUrl() + "/" + stop.getId())).andExpect(status().isOk());
        api(delete(stopsUrl() + "/" + stop.getId())).andExpect(status().isNotFound());
        api(post(stopsUrl()).contentType("application/json").content(json.writeValueAsString(addRequest(place.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stopCount").value(1));
        api(post(stopsUrl()).contentType("application/json").content(json.writeValueAsString(addRequest(place.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stopCount").value(1));
        var other = users.save(new UserAccount("Other", UUID.randomUUID() + "@example.test", "unused", "Balanced traveler"));
        var owner = user;
        user = other;
        api(post(stopsUrl()).contentType("application/json").content(json.writeValueAsString(addRequest(place.getId()))))
                .andExpect(status().isNotFound());
        api(patch(stopsUrl() + "/" + stop.getId() + "/status").contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        api(delete(stopsUrl() + "/" + stop.getId())).andExpect(status().isNotFound());
        assertThat(history(other)).isEmpty();
        user = owner;
        authenticate(owner);
    }
    @Test void authenticatedSavedAndFeedbackApisRejectInjectedActions() throws Exception {
        for (int i = 0; i < 2; i++)
            api(post("/api/saved-places").contentType("application/json").content(json.writeValueAsString(saveRequest(place.getId()))))
                    .andExpect(status().isOk());
        for (int i = 0; i < 2; i++) api(delete("/api/saved-places/" + place.getId())).andExpect(status().isOk());
        for (var action : List.of(NOT_INTERESTED, TOO_EXPENSIVE, TOO_FAR, ALREADY_VISITED)) {
            for (int i = 0; i < 2; i++)
                api(post("/api/taste-feedback").contentType("application/json")
                        .content(json.writeValueAsString(negative(place.getId(), action))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("COFFEE"));
        }
        for (var action : List.of(SAVED, UNSAVED, ADDED_TO_TRIP, REMOVED_FROM_TRIP, VISITED, SKIPPED, REMOVED, REPLACED))
            api(post("/api/taste-feedback").contentType("application/json")
                    .content(json.writeValueAsString(negative(place.getId(), action))))
                    .andExpect(status().isBadRequest());
        api(post("/api/taste-feedback").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        api(post("/api/taste-feedback").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        api(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(negative("unknown", NOT_INTERESTED))))
                .andExpect(status().isNotFound());
        assertThat(history()).hasSize(6);
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/taste-feedback").contentType("application/json")
                .content(json.writeValueAsString(negative(place.getId(), NOT_INTERESTED))))
                .andExpect(status().isForbidden());
    }
}
