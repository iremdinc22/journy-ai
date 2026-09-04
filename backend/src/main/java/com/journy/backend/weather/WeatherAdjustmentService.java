package com.journy.backend.weather;

import com.journy.backend.explore.model.Place;
import com.journy.backend.explore.repository.PlaceRepository;
import com.journy.backend.itinerary.dto.WeatherAdjustmentResponse;
import com.journy.backend.itinerary.model.*;
import com.journy.backend.itinerary.service.PlannerPlaceContract;
import com.journy.backend.trip.model.Trip;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class WeatherAdjustmentService {
    private final WeatherForecastService forecasts;
    private final WeatherRiskEvaluator risk;
    private final PlaceRepository places;
    private final Clock clock;
    public WeatherAdjustmentService(WeatherForecastService forecasts, WeatherRiskEvaluator risk, PlaceRepository places,
                                    @Qualifier("weatherClock") Clock clock) {
        this.forecasts=forecasts;this.risk=risk;this.places=places;this.clock=clock;
    }
    public WeatherAdjustmentResponse preview(Trip trip, List<ItineraryDay> days) {
        WeatherForecast forecast=forecasts.forecastFor(trip);
        if (!"AVAILABLE".equals(forecast.status())) return unavailable(forecast,forecast.reason());
        Map<String,Place> pool=new HashMap<>();
        var ids=days.stream().flatMap(d->d.getStops().stream()).map(ItineraryStop::getPlaceId).filter(Objects::nonNull).distinct().toList();
        places.findAllById(ids).forEach(p->pool.put(p.getId(),p));
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),ZoneId.of(forecast.timezone()));
        for (ItineraryDay day:days.stream().sorted(Comparator.comparingInt(ItineraryDay::getDayNumber)).toList()) {
            LocalDate date=trip.getStartDate().plusDays(day.getDayNumber()-1);
            var stops=day.getStops().stream().sorted(Comparator.comparingInt(ItineraryStop::getStopOrder)).toList();
            for (int i=0;i<stops.size();i++) {
                ItineraryStop outside=stops.get(i); Place outsidePlace=pool.get(outside.getPlaceId());
                if (!verified(outside,outsidePlace,trip) || !sensitive(outsidePlace) || outside.getStatus()!=StopVisitStatus.PLANNED) continue;
                LocalDateTime wetStart=start(date,outside);
                if (wetStart==null || wetStart.isBefore(now)) continue;
                List<WeatherForecast.Hour> wet=window(forecast,wetStart,duration(outsidePlace));
                if(wet.isEmpty() || wet.stream().noneMatch(risk::risky)) continue;
                for (int j:List.of(i-1,i+1)) {
                    if(j<0 || j>=stops.size()) continue;
                    ItineraryStop sheltered=stops.get(j); Place shelteredPlace=pool.get(sheltered.getPlaceId());
                    if (!verified(sheltered,shelteredPlace,trip) || !lowerExposure(shelteredPlace) || sheltered.getStatus()!=StopVisitStatus.PLANNED) continue;
                    LocalDateTime dryStart=start(date,sheltered);
                    if(dryStart==null || dryStart.isBefore(now)) continue;
                    // Do not propose a swap if the longer activity would overlap the next planned slot.
                    if (!fits(stops,j,duration(outsidePlace),date) || !fits(stops,i,duration(shelteredPlace),date)) continue;
                    var dry=window(forecast,dryStart,duration(outsidePlace));
                    if(dry.isEmpty() || dry.stream().anyMatch(risk::risky)) continue;
                    var changes=List.of(new WeatherAdjustmentResponse.StopChange(outside.getId(),outside.getPlaceId(),outside.getTitle(),outside.getTimeWindow(),sheltered.getTimeWindow()),
                            new WeatherAdjustmentResponse.StopChange(sheltered.getId(),sheltered.getPlaceId(),sheltered.getTitle(),sheltered.getTimeWindow(),outside.getTimeWindow()));
                    String signature=trip.getId()+":"+day.getId()+":"+changes+":"+wet+":"+dry+":"+outside.getStopOrder()+":"+sheltered.getStopOrder();
                    String token=UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8)).toString();
                    String window=wetStart.toLocalTime()+" - "+wetStart.plusMinutes(duration(outsidePlace)).toLocalTime();
                    return new WeatherAdjustmentResponse(true,day.getDayNumber(),window,
                            "Rain risk on "+date+" at "+window,
                            "Move "+outside.getTitle()+" to "+sheltered.getTimeWindow()+" and use the lower-exposure stop "+sheltered.getTitle()+" at "+outside.getTimeWindow()+".",
                            outside.getTitle(),sheltered.getTitle(),stops.size(),day.getWalkKm(),stops.size(),day.getWalkKm(),
                            changes.stream().map(c->c.name()+": "+c.fromTime()+" → "+c.toTime()).toList(),
                            List.of("Forecast overlaps this planned visit in "+forecast.timezone(),"The other slot is below both rain thresholds","Existing verified stops only; no distance savings assumed"),
                            "AVAILABLE",token,changes,forecast);
                }
            }
        }
        return unavailable(forecast,"NO_MEANINGFUL_WEATHER_CHANGE");
    }
    public ItineraryDay apply(Trip trip,List<ItineraryDay> days,String previewId) {
        var proposal=preview(trip,days);
        if (!proposal.available() || previewId==null || !previewId.equals(proposal.previewId()))
            throw new ResponseStatusException(CONFLICT,"Weather preview is no longer applicable. Refresh the preview.");
        var day=days.stream().filter(d->d.getDayNumber()==proposal.dayNumber()).findFirst().orElseThrow();
        var first=day.getStops().stream().filter(s->s.getId().equals(proposal.stopChanges().get(0).stopId())).findFirst().orElseThrow();
        var second=day.getStops().stream().filter(s->s.getId().equals(proposal.stopChanges().get(1).stopId())).findFirst().orElseThrow();
        int order=first.getStopOrder();String time=first.getTimeWindow();
        first.setStopOrder(second.getStopOrder());first.setTimeWindow(second.getTimeWindow());
        second.setStopOrder(order);second.setTimeWindow(time);
        day.getStops().sort(Comparator.comparingInt(ItineraryStop::getStopOrder));
        return day;
    }
    private WeatherAdjustmentResponse unavailable(WeatherForecast forecast,String reason) {
        return new WeatherAdjustmentResponse(false,0,null,"No weather adjustment available",reason,null,null,0,0,0,0,List.of(),List.of(reason),forecast.status(),null,List.of(),forecast);
    }
    private boolean verified(ItineraryStop s,Place p,Trip trip) {
        return PlannerPlaceContract.verified(p) && PlannerPlaceContract.inDestination(p,trip.getDestination())
                && Objects.equals(s.getTitle(),p.getName()) && Objects.equals(s.getCategory(),p.getCategory().name())
                && Objects.equals(s.getSource(),"provider:"+p.getProvider())
                && Double.compare(s.getLatitude(),p.getLatitude())==0 && Double.compare(s.getLongitude(),p.getLongitude())==0;
    }
    private boolean sensitive(Place p) {
        return p.getCategory()==com.journy.backend.place.enums.PlaceCategory.WALKING
                || tags(p).matches(".*\\b(outdoor|park|garden|viewpoint)\\b.*");
    }
    private boolean lowerExposure(Place p) {
        if(sensitive(p)) return false;
        return tags(p).matches(".*\\b(indoor|museum|gallery)\\b.*");
    }
    private String tags(Place p) { return p.getTags()==null?"":p.getTags().toLowerCase(Locale.ROOT); }
    // Scheduling assumption when visit duration is unknown; never a fabricated weather observation.
    private int duration(Place p) { return p.getEstimatedVisitMinutes()!=null && p.getEstimatedVisitMinutes()>0 ? p.getEstimatedVisitMinutes():60; }
    private LocalDateTime start(LocalDate date,ItineraryStop stop) {
        try { return date.atTime(LocalTime.parse(stop.getTimeWindow())); } catch (RuntimeException e) { return null; }
    }
    private boolean fits(List<ItineraryStop> stops,int index,int duration,LocalDate date) {
        LocalDateTime begin=start(date,stops.get(index));
        if(begin==null || !begin.plusMinutes(duration).toLocalDate().equals(date)) return false;
        if(index+1==stops.size()) return true;
        LocalDateTime next=start(date,stops.get(index+1));
        return next!=null && !begin.plusMinutes(duration).isAfter(next);
    }
    private List<WeatherForecast.Hour> window(WeatherForecast forecast,LocalDateTime start,int minutes) {
        LocalDateTime end=start.plusMinutes(minutes), first=start.withMinute(0).withSecond(0);
        List<WeatherForecast.Hour> result=new ArrayList<>();
        for(LocalDateTime t=first;t.isBefore(end);t=t.plusHours(1)) {
            LocalDateTime target=t;
            var hour=forecast.hours().stream().filter(h->h.time().equals(target)).findFirst().orElse(null);
            if(hour==null || hour.precipitationProbability()==null || hour.precipitationAmount()==null) return List.of();
            result.add(hour);
        }
        return result;
    }
}
