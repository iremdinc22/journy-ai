package com.journy.backend.weather;

import com.journy.backend.destination.service.DestinationResolutionService;
import com.journy.backend.trip.model.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherForecastService {
    private final WeatherProvider provider;
    private final DestinationResolutionService destinations;
    private final WeatherRiskEvaluator risk;
    private final Clock clock;
    private final Duration ttl;
    private record Cached(Instant expires, WeatherForecast forecast) {}
    private final Map<String,Cached> cache = new ConcurrentHashMap<>();
    public WeatherForecastService(WeatherProvider provider, DestinationResolutionService destinations, WeatherRiskEvaluator risk,
                                  @Qualifier("weatherClock") Clock clock, @Value("${journy.weather.cache-ttl:PT15M}") Duration ttl) {
        this.provider=provider;this.destinations=destinations;this.risk=risk;this.clock=clock;this.ttl=ttl;
    }
    public WeatherForecast forecastFor(Trip trip) {
        if (trip==null || trip.getStartDate()==null || trip.getEndDate()==null) return WeatherForecast.unavailable("INVALID_TRIP",null,null,null,null);
        LocalDate start=trip.getStartDate(), end=trip.getEndDate().minusDays(1); // Trip end date is exclusive throughout Journy.
        try {
            var resolved=destinations.resolve(trip.destinationLookupQuery()).orElse(null);
            if (resolved==null) return WeatherForecast.unavailable("DESTINATION_UNRESOLVED",null,null,start,end);
            String key=resolved.latitude()+":"+resolved.longitude()+":"+start+":"+end+":auto";
            Cached cached=cache.get(key);
            if(cached!=null && cached.expires().isAfter(clock.instant())) return cached.forecast();
            WeatherForecast forecast=provider.forecast(resolved.latitude(),resolved.longitude(),start,end,"auto");
            if (cache.size()>=256) cache.clear();
            cache.put(key,new Cached(clock.instant().plus("AVAILABLE".equals(forecast.status())?ttl:Duration.ofSeconds(30)),forecast));
            return forecast;
        } catch (RuntimeException e) { return WeatherForecast.unavailable("PROVIDER_FAILURE",null,null,start,end); }
    }
    // Legacy RightNow consumer: no guessed windows, unavailable and dry never become a rain alert.
    public Optional<RainForecast> rainForecastFor(Trip trip,int dayNumber) {
        WeatherForecast forecast=forecastFor(trip);
        if (!"AVAILABLE".equals(forecast.status())) return Optional.empty();
        LocalDate date=trip.getStartDate().plusDays(dayNumber-1);
        return forecast.hours().stream().filter(h->h.time().toLocalDate().equals(date) && risk.risky(h)).findFirst()
                .map(h->new RainForecast(h.time().toLocalTime()+" - "+h.time().plusHours(1).toLocalTime(),h.precipitationProbability().intValue(),h.precipitationAmount(),h.weatherCode(),forecast.source()));
    }
    public record RainForecast(String rainWindow,int precipitationProbability,double rainMm,Integer weatherCode,String source) {}
}
