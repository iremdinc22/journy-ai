package com.journy.backend.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.journy.backend.destination.provider.*;
import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.model.*;
import com.journy.backend.place.enums.PlaceCategory;
import com.journy.backend.trip.model.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherReliabilityTest {
    static final LocalDate DATE=LocalDate.of(2026,9,5);
    final Clock clock=Clock.fixed(Instant.parse("2026-09-04T01:00:00Z"),ZoneOffset.UTC);
    final WeatherRiskEvaluator risk=new WeatherRiskEvaluator(60,1);
    Trip trip(String city) { var t=new Trip();t.setId(city);t.setDestination(city);t.setStartDate(DATE);t.setEndDate(DATE.plusDays(1));return t; }
    WeatherForecast forecast(double probability,double amount) {
        var hours=new ArrayList<WeatherForecast.Hour>();
        for(int h=0;h<24;h++) hours.add(new WeatherForecast.Hour(DATE.atTime(h,0),h==14?probability:0d,h==14?amount:0d,61));
        return new WeatherForecast("AVAILABLE","FORECAST_AVAILABLE","fixture",41d,26d,"Europe/Istanbul",DATE,DATE,hours);
    }
    Place place(String id,PlaceCategory category,String tags) {
        var p=new Place(id,"Edirne",category,"Fixture","Mid",4.5,"");p.setId(id);p.setName(id);p.setCity("Edirne");p.setCategory(category);p.setTags(tags);
        p.setProvider("osm");p.setProviderFetchedAt(clock.instant());p.setLatitude(41.67);p.setLongitude(26.56);p.setEstimatedVisitMinutes(60);return p;
    }
    ItineraryDay day(Place... places) {
        var d=new ItineraryDay(trip("Edirne"),1,"Original title","Original summary",3);
        int i=0;for(var p:places) d.addStop(new ItineraryStop(++i,p.getName(),p.getCategory().name(),i==1?"14:00":"16:00",p.getId(),"provider:osm","",p.getLatitude(),p.getLongitude()));return d;
    }
    WeatherAdjustmentService service(WeatherForecast f,Place... pool) {
        var forecasts=mock(WeatherForecastService.class);when(forecasts.forecastFor(any())).thenReturn(f);
        var repo=mock(PlaceRepository.class);when(repo.findAllById(any())).thenReturn(List.of(pool));
        return new WeatherAdjustmentService(forecasts,risk,repo,clock);
    }
    @Test void zeroAndLowRainNeverMutateOrOfferAdjustment() {
        var outdoor=place("Park",PlaceCategory.WALKING,"");var indoor=place("Museum",PlaceCategory.CULTURE,"indoor,museum");var d=day(outdoor,indoor);
        for(double p:List.of(0d,10d,59d)) assertThat(service(forecast(p,0),outdoor,indoor).preview(d.getTrip(),List.of(d)).available()).isFalse();
        assertThat(d.getStops().getFirst().getTimeWindow()).isEqualTo("14:00");
    }
    @Test void rainProducesSpecificPreviewAndExplicitIdentityPreservingApply() {
        var outdoor=place("Park",PlaceCategory.WALKING,"");var indoor=place("Museum",PlaceCategory.CULTURE,"indoor");var d=day(outdoor,indoor);var service=service(forecast(60,0),outdoor,indoor);
        var preview=service.preview(d.getTrip(),List.of(d));assertThat(preview.available()).isTrue();assertThat(preview.dayNumber()).isEqualTo(1);
        assertThat(preview.stopChanges()).hasSize(2);assertThat(d.getStops().getFirst().getTitle()).isEqualTo("Park");
        var identities=d.getStops().stream().map(s->s.getId()+s.getPlaceId()+s.getTitle()+s.getLatitude()+s.getLongitude()+s.getSource()).sorted().toList();
        service.apply(d.getTrip(),List.of(d),preview.previewId());
        assertThat(d.getStops().getFirst().getTitle()).isEqualTo("Museum");assertThat(d.getStops().getLast().getTimeWindow()).isEqualTo("16:00");
        assertThat(d.getStops().stream().map(s->s.getId()+s.getPlaceId()+s.getTitle()+s.getLatitude()+s.getLongitude()+s.getSource()).sorted().toList()).isEqualTo(identities);
        assertThat(d.getTitle()).isEqualTo("Original title");assertThat(d.getWalkKm()).isEqualTo(3);
        assertThatThrownBy(()->service.apply(d.getTrip(),List.of(d),preview.previewId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void indoorOnlyUnknownExposureAndMissingIdentityHaveNoPreview() {
        var museum=place("Museum",PlaceCategory.CULTURE,"indoor");var cafe=place("Cafe",PlaceCategory.COFFEE,"");
        assertThat(service(forecast(90,4),museum,cafe).preview(trip("Edirne"),List.of(day(museum,cafe))).available()).isFalse();
        var park=place("Park",PlaceCategory.WALKING,"");var d=day(park,cafe);
        assertThat(service(forecast(90,4),park,cafe).preview(d.getTrip(),List.of(d)).available()).isFalse();
        cafe.setTags("indoor");d.getStops().getFirst().setTitle("Invented park");
        assertThat(service(forecast(90,4),park,cafe).preview(d.getTrip(),List.of(d)).available()).isFalse();
    }
    @Test void unavailableHasNoPreview() {
        var p=service(WeatherForecast.unavailable("PROVIDER_FAILURE",41d,26d,DATE,DATE)).preview(trip("Edirne"),List.of());
        assertThat(p.available()).isFalse();assertThat(p.weatherStatus()).isEqualTo("UNAVAILABLE");assertThat(p.forecast().hours()).isEmpty();
    }
    @Test void thresholdUsesProbabilityOrAmountNeverWeatherCodeAlone() {
        assertThat(risk.risky(new WeatherForecast.Hour(DATE.atStartOfDay(),59d,.99,95))).isFalse();
        assertThat(risk.risky(new WeatherForecast.Hour(DATE.atStartOfDay(),60d,0d,0))).isTrue();
        assertThat(risk.risky(new WeatherForecast.Hour(DATE.atStartOfDay(),0d,1d,0))).isTrue();
    }
    @Test void coordinatesDatesAndCacheAreIsolated() {
        var resolver=mock(DestinationResolutionService.class);
        when(resolver.resolve("Edirne")).thenReturn(Optional.of(new ResolvedDestination("Edirne","Edirne","Edirne",null,"TR",41.67,26.56,"fixture","1")));
        when(resolver.resolve("Las Vegas")).thenReturn(Optional.of(new ResolvedDestination("Las Vegas","Las Vegas","Las Vegas",null,"US",36.17,-115.14,"fixture","2")));
        var count=new AtomicInteger();WeatherProvider provider=(lat,lon,start,end,zone)->{count.incrementAndGet();assertThat(start).isEqualTo(DATE);assertThat(end).isEqualTo(DATE);return forecast(lat>40?90:0,0);};
        var service=new WeatherForecastService(provider,resolver,risk,clock,Duration.ofMinutes(15));
        assertThat(service.rainForecastFor(trip("Edirne"),1)).isPresent();assertThat(service.rainForecastFor(trip("Las Vegas"),1)).isEmpty();
        service.forecastFor(trip("Edirne"));assertThat(count.get()).isEqualTo(2);
    }
    @Test void providerExceptionIsUnavailable() {
        var resolver=mock(DestinationResolutionService.class);when(resolver.resolve(anyString())).thenReturn(Optional.of(new ResolvedDestination("x","x","x",null,"TR",41,26,"fixture","1")));
        var service=new WeatherForecastService((a,b,c,d,e)->{throw new IllegalStateException("timeout");},resolver,risk,clock,Duration.ofMinutes(15));
        assertThat(service.forecastFor(trip("Edirne")).status()).isEqualTo("UNAVAILABLE");
    }
    @Test void parserRespectsDestinationLocalDayAndRejectsMissingData() throws Exception {
        var provider=new OpenMeteoWeatherProvider(RestClient.builder(),true,"http://unused",clock);
        // At 01:00 UTC Los Angeles is still September 3, Tokyo is September 4.
        LocalDate previous=LocalDate.of(2026,9,3);
        var root=new ObjectMapper().createObjectNode();root.put("timezone","America/Los_Angeles");var hourly=root.putObject("hourly");
        var times=hourly.putArray("time");var probabilities=hourly.putArray("precipitation_probability");var amounts=hourly.putArray("precipitation");
        for(int h=0;h<24;h++){times.add(previous.atTime(h,0).toString());probabilities.add(0);amounts.add(0);}
        assertThat(provider.parse(root,36,-115,previous,previous).status()).isEqualTo("AVAILABLE");
        root.put("timezone","Asia/Tokyo");assertThat(provider.parse(root,35,139,previous,previous).reason()).isEqualTo("OUTSIDE_FORECAST_HORIZON");
        root.put("timezone","America/Los_Angeles");probabilities.remove(0);assertThat(provider.parse(root,36,-115,previous,previous).status()).isEqualTo("UNAVAILABLE");
    }
    @Test void httpProviderUsesExactCoordinatesDatesAndHandlesFailure() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var query=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/forecast",exchange->{
            query.set(exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(503,-1);exchange.close();
        });
        server.start();
        try {
            var provider=new OpenMeteoWeatherProvider(RestClient.builder(),true,"http://127.0.0.1:"+server.getAddress().getPort()+"/forecast",clock);
            assertThat(provider.forecast(36.17,-115.14,DATE,DATE,"auto").status()).isEqualTo("UNAVAILABLE");
            assertThat(query.get()).contains("latitude=36.17","longitude=-115.14","start_date=2026-09-05","end_date=2026-09-05","timezone=auto");
        } finally { server.stop(0); }
    }
    @Test void rainOutsideVisitOrWithoutDrySlotCannotProducePreview() {
        var park=place("Park",PlaceCategory.WALKING,"");var museum=place("Museum",PlaceCategory.CULTURE,"indoor");var d=day(park,museum);
        var base=forecast(90,3);
        var allWet=new WeatherForecast(base.status(),base.reason(),base.source(),base.latitude(),base.longitude(),base.timezone(),DATE,DATE,
                base.hours().stream().map(h->new WeatherForecast.Hour(h.time(),90d,3d,61)).toList());
        assertThat(service(allWet,park,museum).preview(d.getTrip(),List.of(d)).available()).isFalse();
        d.getStops().getFirst().setTimeWindow("10:00");
        assertThat(service(base,park,museum).preview(d.getTrip(),List.of(d)).available()).isFalse();
    }
    @Test void outsideHorizonAndDisabledProviderDoNotQuery() {
        var provider=new OpenMeteoWeatherProvider(RestClient.builder(),true,"http://unused",clock);
        assertThat(provider.forecast(41,26,DATE.plusMonths(2),DATE.plusMonths(2),"auto").reason()).isEqualTo("OUTSIDE_FORECAST_HORIZON");
        assertThat(new OpenMeteoWeatherProvider(RestClient.builder(),false,"http://unused",clock).forecast(41,26,DATE,DATE,"auto").status()).isEqualTo("UNAVAILABLE");
    }
}
