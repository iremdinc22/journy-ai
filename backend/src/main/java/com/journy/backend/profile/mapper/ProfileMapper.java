package com.journy.backend.profile.mapper;

import com.journy.backend.profile.dto.ProfileResponse;
import com.journy.backend.savedplace.model.SavedPlace;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class ProfileMapper {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    public ProfileResponse toResponse(UserAccount user, Trip currentTrip, List<Trip> savedTrips, List<SavedPlace> savedPlaces) {
        return new ProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getTravelStyle(),
                new ProfileResponse.CurrentTrip(
                        currentTrip.getDestination(),
                        DATE_FORMATTER.format(currentTrip.getStartDate()) + " - " + DATE_FORMATTER.format(currentTrip.getEndDate()),
                        currentTrip.getTotalStops(),
                        currentTrip.getFoodPicks(),
                        currentTrip.getAverageWalkKm()
                ),
                tasteSignals(),
                savedTrips.stream().map(this::toSavedPlan).toList(),
                savedPlaces.stream().map(this::toSavedPlace).toList()
        );
    }

    private ProfileResponse.SavedPlan toSavedPlan(Trip trip) {
        return new ProfileResponse.SavedPlan(
                trip.getId(),
                trip.getDestination(),
                trip.dayCount() + " days · " + trip.getPace().name(),
                trip.getTotalStops(),
                trip.getFoodPicks(),
                trip.getAverageWalkKm()
        );
    }

    private ProfileResponse.SavedPlace toSavedPlace(SavedPlace place) {
        return new ProfileResponse.SavedPlace(
                place.getPlaceId(),
                place.getName(),
                place.getCity(),
                place.getCategory(),
                place.getImageUrl(),
                place.getRating()
        );
    }

    private List<ProfileResponse.TasteSignal> tasteSignals() {
        return List.of(
                new ProfileResponse.TasteSignal("Local food", "Try the real flavors", "restaurant"),
                new ProfileResponse.TasteSignal("Museums", "Culture & history", "museum"),
                new ProfileResponse.TasteSignal("Coffee breaks", "Best cafes & spots", "coffee"),
                new ProfileResponse.TasteSignal("Easy walking", "Comfortable pace", "walk")
        );
    }
}
