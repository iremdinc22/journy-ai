package com.journy.backend.weather;

import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.*;
import com.journy.backend.itinerary.repository.ItineraryDayRepository;
import com.journy.backend.itinerary.service.ItineraryService;
import com.journy.backend.trip.enums.*;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:weather_apply;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class WeatherApplyIntegrationTest {
    @Autowired ItineraryService itineraries;
    @Autowired ItineraryDayRepository days;
    @Autowired TripRepository trips;
    @Autowired PlaceRepository places;
    @Autowired UserAccountRepository users;
    @Autowired EntityManager entityManager;
    @MockitoBean WeatherForecastService forecasts;

    @Test @Transactional void explicitApplyPersistsExactStopsAndRejectsAnotherOwner() {
        var fixtures=new WeatherReliabilityTest();
        var user=users.save(new UserAccount("Weather","weather-integration@example.test","unused","Balanced traveler"));
        var tomorrow=LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(1);
        var trip=trips.save(new Trip(user,"Edirne","",tomorrow,tomorrow.plusDays(1),TravelerType.SOLO,BudgetMode.BALANCED,TripPace.BALANCED,Set.of(TravelInterest.WALKING)));
        var park=places.save(fixtures.place("Verified park",com.journy.backend.place.enums.PlaceCategory.WALKING,"outdoor"));
        var museum=places.save(fixtures.place("Verified museum",com.journy.backend.place.enums.PlaceCategory.CULTURE,"indoor,museum"));
        var day=fixtures.day(park,museum);day.setTrip(trip);days.saveAndFlush(day);
        var base=fixtures.forecast(90,2);
        var forecast=new WeatherForecast(base.status(),base.reason(),base.source(),base.latitude(),base.longitude(),base.timezone(),tomorrow,tomorrow,
                base.hours().stream().map(h->new WeatherForecast.Hour(tomorrow.atTime(h.time().toLocalTime()),h.precipitationProbability(),h.precipitationAmount(),h.weatherCode())).toList());
        when(forecasts.forecastFor(any())).thenReturn(forecast);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(),null,List.of()));
            var preview=itineraries.weatherAdjustment(trip.getId());assertThat(preview.available()).isTrue();
            assertThat(days.findById(day.getId()).orElseThrow().getStops().getFirst().getPlaceId()).isEqualTo(park.getId());
            itineraries.applyWeatherAdjustment(trip.getId(),preview.previewId());entityManager.flush();entityManager.clear();
            var saved=days.findById(day.getId()).orElseThrow();
            assertThat(saved.getStops().getFirst().getPlaceId()).isEqualTo(museum.getId());
            assertThat(saved.getStops().getLast().getPlaceId()).isEqualTo(park.getId());
            assertThat(saved.getStops().getLast().getTimeWindow()).isEqualTo("16:00");
            var other=users.save(new UserAccount("Other","other-weather@example.test","unused","Balanced traveler"));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other.getEmail(),null,List.of()));
            assertThatThrownBy(()->itineraries.applyWeatherAdjustment(trip.getId(),preview.previewId())).isInstanceOf(com.journy.backend.common.exception.ResourceNotFoundException.class);
        } finally {SecurityContextHolder.clearContext();}
    }
}
