package com.journy.backend.startarea;

import com.journy.backend.destination.provider.ResolvedDestination;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.provider.PlaceProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartAreaSuggestionServiceTest {
    final ResolvedDestination destination = new ResolvedDestination("Test locality","Test locality","Test locality",null,"Test",46,14,"fixture","relation/1");
    StartAreaSuggestion place(String id, String name, String providerId) {
        return new StartAreaSuggestion(id,name,"square",46.001,14.001,"provider:fixture",providerId);
    }
    @Test void fallbackIdentityDeduplicatesNamesAndRoundedCoordinatesAndStrongIdentityWins() {
        var resolver=mock(DestinationResolutionService.class);
        when(resolver.resolve("Test locality")).thenReturn(Optional.of(destination));
        var provider=mock(PlaceProvider.class);
        when(provider.searchStartAreas(destination,"",60)).thenReturn(List.of(
            place("one","Local Square",null),place("two","LOCAL SQUARE",null),
            place("three","Main Square","node/1"),place("four","Duplicate provider record","node/1")));
        var service=new StartAreaSuggestionService(resolver,List.of(provider));
        var results=service.suggestions("Test locality","");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(StartAreaSuggestion::id).doesNotHaveDuplicates();
        assertThat(service.suggestions("Test locality","")).isEqualTo(results);
        verify(provider,times(1)).searchStartAreas(destination,"",60);
    }
    @Test void separateProviderEntitiesWithTheSameChipLabelDisplayOnlyTheNearestIdentity() {
        var resolver=mock(DestinationResolutionService.class);
        when(resolver.resolve("Test locality")).thenReturn(Optional.of(destination));
        var provider=mock(PlaceProvider.class);
        var near=place("one","Named station","node/1");
        var far=new StartAreaSuggestion("two","Named station","square",46.02,14.02,"provider:fixture","node/2");
        when(provider.searchStartAreas(destination,"",60)).thenReturn(List.of(far,near));
        var service=new StartAreaSuggestionService(resolver,List.of(provider));
        assertThat(service.suggestions("Test locality","")).singleElement().satisfies(p -> {
            assertThat(p.providerPlaceId()).isEqualTo("node/1");
            assertThat(p.latitude()).isEqualTo(near.latitude());
        });
    }
    @Test void validationRejectsInvalidUnnamedUnrelatedAndSyntheticProviderData() {
        assertThat(StartAreaSuggestionService.valid(destination,place("one","Real square","node/1"))).isTrue();
        for(var invalid:List.of(
            new StartAreaSuggestion("id"," ",null,46.0,14.0,"provider:osm","node/1"),
            new StartAreaSuggestion("id","Invalid",null,0.0,0.0,"provider:osm","node/1"),
            new StartAreaSuggestion("id","Invalid",null,Double.NaN,14.0,"provider:osm","node/1"),
            new StartAreaSuggestion("id","Distant",null,35.0,139.0,"provider:osm","node/1"),
            new StartAreaSuggestion("id","Seed",null,46.0,14.0,"provider:seed","node/1"),
            new StartAreaSuggestion("id","Missing coordinates",null,null,14.0,"provider:osm","node/1"))) {
            assertThat(StartAreaSuggestionService.valid(destination,invalid)).isFalse();
        }
    }
    @Test void exceptionAndNoDestinationReturnEmptyAndOptionalStartDoesNotCallProvider() {
        var resolver=mock(DestinationResolutionService.class);
        when(resolver.resolve("Test locality")).thenReturn(Optional.of(destination));
        when(resolver.resolve("Unresolvable")).thenReturn(Optional.empty());
        var provider=mock(PlaceProvider.class);
        when(provider.searchStartAreas(destination,"",60)).thenThrow(new IllegalStateException("Provider offline"));
        var service=new StartAreaSuggestionService(resolver,List.of(provider));
        assertThat(service.suggestions("Test locality","")).isEmpty();
        assertThat(service.suggestions("Unresolvable","")).isEmpty();
        assertThat(service.verify(destination,null,"")).isNull();
    }
}
