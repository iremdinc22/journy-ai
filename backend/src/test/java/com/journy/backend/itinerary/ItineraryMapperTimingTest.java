package com.journy.backend.itinerary;

import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ItineraryMapperTimingTest {
    final ItineraryMapper mapper = new ItineraryMapper();
    ItineraryDay day(String firstTime, String secondTime) {
        var day = new ItineraryDay(null, 1, "Recorded route", "", 3.4);
        day.addStop(new ItineraryStop(1, "Balkan Savaşı Şehitleri Anıtı", "WALKING", firstTime,
                "osm_node_1801477226", "provider:osm", "", 41.6592439, 26.5377829));
        day.addStop(new ItineraryStop(2, "Edirne Müzesi", "CULTURE", secondTime,
                "osm_node_2181137651", "provider:osm", "", 41.678894, 26.5607674));
        return day;
    }
    @Test void explicitSlotsOverrideDerivedTimesWithoutRemovingTravel() {
        var day = day("14:00", "16:00");
        var before = mapper.toDayResponse(day);
        assertThat(before.timeline().stream().filter(t -> t.type().equals("STOP")).map(t -> t.startTime())).containsExactly("14:00", "16:00");
        assertThat(before.timeline().stream().filter(t -> t.type().equals("TRAVEL"))).singleElement().satisfies(t -> {
            assertThat(t.durationMinutes()).isPositive(); assertThat(t.distanceKm()).isPositive();
        });
        assertThat(day.getStops().getLast().getTimeWindow()).isEqualTo("16:00");
    }
    @Test void persistedSwapChangesOnlyTimeAndOrderAndTimelineAgrees() {
        var day = day("14:00", "16:00");
        var first = day.getStops().getFirst(); var second = day.getStops().getLast();
        var identities = mapper.toDayResponse(day).stops().stream().map(s -> List.of(s.id(), s.placeId(), s.source(), s.title(), s.category(), s.latitude(), s.longitude())).toList();
        first.setTimeWindow("16:00"); first.setStopOrder(2);
        second.setTimeWindow("14:00"); second.setStopOrder(1);
        day.getStops().clear(); day.addStop(second); day.addStop(first);
        var after = mapper.toDayResponse(day);
        assertThat(after.stops().stream().map(s -> List.of(s.id(), s.placeId(), s.source(), s.title(), s.category(), s.latitude(), s.longitude())).toList()).containsExactlyElementsOf(identities.reversed());
        for (var stop : after.stops()) {
            assertThat(after.timeline().stream().filter(t -> t.id().equals(stop.id())).findFirst().orElseThrow().startTime()).isEqualTo(stop.timeWindow());
        }
        assertThat(after.walkKm()).isEqualTo(3.4);
    }
    @Test void missingOrInvalidLegacySlotsKeepDerivedFallback() {
        for (String missing : new String[]{null, "", "Afternoon", "24:00", "16:75"}) {
            var result = mapper.toDayResponse(day("14:00", missing));
            assertThat(result.timeline().getLast().startTime()).isEqualTo("15:55");
        }
        assertThat(mapper.toDayResponse(day(null, null)).timeline().getFirst().startTime()).isEqualTo("09:30");
        assertThat(mapper.toDayResponse(day("99:99", "16:00")).timeline().getLast().startTime()).isEqualTo("16:00");
    }
}
