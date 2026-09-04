package com.journy.backend.itinerary;

import com.journy.backend.explore.model.Place;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.service.PlannerPlaceContract;
import com.journy.backend.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class PlannerPlaceContractTest {
    @Test
    void unknownIdentityAndCorruptedSelectionsFailBeforePersistence() {
        var pool = PlannerPlaceContract.snapshot(List.of(place()), "Sarajevo");
        List<Consumer<ItineraryStop>> corruptions = List.of(
                stop -> stop.setPlaceId("D"), stop -> stop.setPlaceId(null),
                stop -> stop.setTitle("Invented name"), stop -> stop.setLatitude(43.861),
                stop -> stop.setLongitude(18.431), stop -> stop.setSource("provider:invented"),
                stop -> stop.setCategory("FOOD"));
        for (var corrupt : corruptions) {
            var day = day();
            corrupt.accept(day.getStops().getFirst());
            assertThatThrownBy(() -> PlannerPlaceContract.validate(List.of(day), pool))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("accepted candidate");
        }
    }

    @Test
    void snapshotIsImmutableAndDoesNotTrustLaterPlaceMutation() {
        Place place = place();
        var pool = PlannerPlaceContract.snapshot(List.of(place), "Sarajevo");
        place.setName("Mutated name");
        place.setLatitude(0.0);
        assertThatCode(() -> PlannerPlaceContract.validate(List.of(day()), pool)).doesNotThrowAnyException();
        assertThat(pool.get("A").name()).isEqualTo("Museum");
    }

    @Test
    void duplicateAcrossDaysIsRejectedAndWrongDestinationIsNotAccepted() {
        var pool = PlannerPlaceContract.snapshot(List.of(place()), "Sarajevo");
        assertThatThrownBy(() -> PlannerPlaceContract.validate(List.of(day(), day()), pool))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
        assertThat(PlannerPlaceContract.snapshot(List.of(place()), "Tallinn")).isEmpty();
    }

    @Test
    void newlyGeneratedUnverifiedStopsAreRejectedEvenIfTheyHaveAnId() {
        var pool = PlannerPlaceContract.snapshot(List.of(place()), "Sarajevo");
        for (String source : new String[]{null, "planned_fallback", "repository:seed"}) {
            var day = day();
            day.getStops().getFirst().setSource(source);
            assertThatThrownBy(() -> PlannerPlaceContract.validate(List.of(day), pool))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("provider-backed");
        }
    }

    private Place place() {
        Place place = new Place();
        place.setId("A"); place.setName("Museum"); place.setCity("Sarajevo");
        place.setProvider("osm"); place.setProviderFetchedAt(Instant.now());
        place.setCategory(PlaceCategory.CULTURE); place.setLatitude(43.86); place.setLongitude(18.43);
        return place;
    }

    private ItineraryDay day() {
        var day = new ItineraryDay(null, 1, "Title", "Summary", 2);
        day.addStop(new ItineraryStop(1, "Museum", "CULTURE", "09:30", "A", "provider:osm", "", 43.86, 18.43));
        return day;
    }
}
