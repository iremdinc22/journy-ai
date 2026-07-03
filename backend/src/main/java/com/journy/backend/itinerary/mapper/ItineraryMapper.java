package com.journy.backend.itinerary.mapper;

import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.trip.model.Trip;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItineraryMapper {
    public ItineraryResponse toResponse(Trip trip, List<ItineraryDay> days) {
        return new ItineraryResponse(
                trip.getId(),
                trip.getDestination(),
                days.stream().map(this::toDayResponse).toList()
        );
    }

    private ItineraryResponse.ItineraryDayResponse toDayResponse(ItineraryDay day) {
        return new ItineraryResponse.ItineraryDayResponse(
                day.getDayNumber(),
                day.getTitle(),
                day.getSummary(),
                day.getWalkKm(),
                day.getStops().size(),
                day.getStops().stream().map(this::toStopResponse).toList()
        );
    }

    private ItineraryResponse.ItineraryStopResponse toStopResponse(ItineraryStop stop) {
        return new ItineraryResponse.ItineraryStopResponse(
                stop.getStopOrder(),
                stop.getTitle(),
                stop.getCategory(),
                stop.getTimeWindow(),
                stop.getNote(),
                stop.getLatitude(),
                stop.getLongitude()
        );
    }
}
