package com.journy.backend.explore.search;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSearchQueryAdversarialTest {
    @ParameterizedTest
    @CsvSource({
            "coffee,COFFEE,", "cafe,COFFEE,", "kahve,COFFEE,", "kafe,COFFEE,",
            "museum,CULTURE,museum", "müze,CULTURE,museum", "gallery,CULTURE,gallery",
            "galeri,CULTURE,gallery", "culture,CULTURE,", "kültür,CULTURE,",
            "food,FOOD,", "restaurant,FOOD,", "yemek,FOOD,", "restoran,FOOD,",
            "walking,WALKING,", "walk,WALKING,", "yürüyüş,WALKING,", "park,WALKING,park"
    })
    void existingAliasesRemainExplicitAndStable(String input, PlaceCategory category, String type) {
        var query = PlaceSearchQuery.of(input);
        assertThat(query.category()).isEqualTo(category);
        assertThat(query.type()).isEqualTo(type == null ? "" : type);
    }

    @ParameterizedTest
    @CsvSource({"Baščaršija,bascarsija", "Muzej Izetbegovića,muzej izetbegovica", "Škofljica,skofljica", "İSTANBUL,istanbul"})
    void supportedUnicodeNormalizationIsLocaleIndependent(String input, String normalized) {
        assertThat(PlaceSearchQuery.normalize(input)).isEqualTo(normalized);
    }

    @Test void canonicalUnicodeDisplayTextIsNeverChangedByMatching() {
        var place = new Place("Baščaršija", "Sarajevo", PlaceCategory.CULTURE, "Provider", "Mid", 0, "image");
        assertThat(PlaceSearchQuery.of("BASC").matches(place)).isTrue();
        assertThat(place.getName()).isEqualTo("Baščaršija");
        assertThat(PlaceSearchQuery.normalize("Ølbaren")).isEqualTo("ølbaren");
    }
}
