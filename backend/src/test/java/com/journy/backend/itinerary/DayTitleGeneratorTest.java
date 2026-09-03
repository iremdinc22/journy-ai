package com.journy.backend.itinerary;

import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.itinerary.mapper.ItineraryMapper;
import com.journy.backend.itinerary.service.DayTitleGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DayTitleGeneratorTest {
    @Test
    void museumAndCafeReflectCultureAndCoffeeInWholeTranslations() {
        var stops = List.of(stop(1, "Konak", "CULTURE"), stop(2, "Kafanica", "COFFEE"));
        assertThat(DayTitleGenerator.title(stops, "tr")).isEqualTo("Konak: Kültür ve Kahve");
        assertThat(DayTitleGenerator.title(stops, "en")).isEqualTo("Konak: Culture & Coffee")
                .doesNotContain("Museum Morning", "Old Town");
    }

    @Test
    void foodAndSocialStopsUseExistingCategoriesWithoutInventingNightlife() {
        var stops = List.of(stop(1, "Velika Bašta", "FOOD"), stop(2, "City Pub", "FOOD"), stop(3, "SO.BA", "COFFEE"));
        assertThat(DayTitleGenerator.title(stops, "tr")).isEqualTo("Velika Bašta: Yerel Lezzetler ve Kahve")
                .doesNotContain("Local", "Bites", "Easy", "Lunch", "Night");
    }

    @Test
    void unknownMixUsesNeutralFallbackAndPlannedStopsCannotSupplyThemeOrAnchor() {
        var fallback = stop(2, "Fake Museum", "CULTURE");
        fallback.setSource("planned_fallback"); fallback.setPlaceId(null);
        assertThat(DayTitleGenerator.title(List.of(stop(1, "Unknown", "UNKNOWN"), fallback), "tr"))
                .isEqualTo("Şehir Keşfi");
        assertThat(DayTitleGenerator.title(List.of(), "en")).isEqualTo("City Discovery");
    }

    @Test
    void titlesAreDeterministicAndFollowVisitOrderForTies() {
        var museum = stop(1, "Konak", "CULTURE");
        var cafe = stop(2, "Art", "COFFEE");
        assertThat(DayTitleGenerator.translations(List.of(museum, cafe)))
                .isEqualTo(DayTitleGenerator.translations(List.of(cafe, museum)));
        museum.setStopOrder(3);
        assertThat(DayTitleGenerator.title(List.of(museum, cafe), "tr")).isEqualTo("Art: Kahve ve Kültür");
    }

    @Test
    void arbitraryPlaceNamesWorkWithoutCityHardcodesAndLongNamesUseCategoryFallback() {
        for (String name : List.of("Maiasmokk", "Zmajevac", "Arbitrary Anchor")) {
            assertThat(DayTitleGenerator.title(List.of(stop(1, name, "WALKING")), "tr"))
                    .isEqualTo(name + ": Yürüyüş");
        }
        assertThat(DayTitleGenerator.title(List.of(stop(1, "A Very Long Museum Name That Will Not Fit", "CULTURE"),
                stop(2, "Cafe", "COFFEE")), "tr")).isEqualTo("Kültür ve Kahve Rotası");
    }

    @Test
    void titleMappingDoesNotMutatePersistedStopIdentityOrCoordinates() {
        var stop = stop(1, "Art", "COFFEE");
        var day = new ItineraryDay(null, 1, "Persisted title", "Summary", 2);
        day.addStop(stop);
        var mapped = new ItineraryMapper().toDayResponse(day);
        assertThat(mapped.titleTranslations().get("tr")).isEqualTo("Art: Kahve");
        assertThat(day.getTitle()).isEqualTo("Persisted title");
        assertThat(mapped.stops()).singleElement().satisfies(value -> {
            assertThat(value.title()).isEqualTo("Art"); assertThat(value.placeId()).isEqualTo("osm_1");
            assertThat(value.source()).isEqualTo("provider:osm");
            assertThat(value.latitude()).isEqualTo(43.86); assertThat(value.longitude()).isEqualTo(18.43);
        });
        assertThat(stop.getTitle()).isEqualTo("Art"); assertThat(stop.getPlaceId()).isEqualTo("osm_1");
        assertThat(stop.getLatitude()).isEqualTo(43.86); assertThat(stop.getLongitude()).isEqualTo(18.43);
    }

    private ItineraryStop stop(int order, String name, String category) {
        return new ItineraryStop(order, name, category, "09:30", "osm_" + order, "provider:osm", "", 43.86, 18.43);
    }
}
