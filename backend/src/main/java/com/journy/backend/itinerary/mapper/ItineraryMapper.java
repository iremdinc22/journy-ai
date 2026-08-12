package com.journy.backend.itinerary.mapper;

import com.journy.backend.itinerary.dto.ItineraryResponse;
import com.journy.backend.itinerary.model.ItineraryDay;
import com.journy.backend.itinerary.model.ItineraryStop;
import com.journy.backend.trip.model.Trip;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ItineraryMapper {
    public ItineraryResponse toResponse(Trip trip, List<ItineraryDay> days) {
        return new ItineraryResponse(
                trip.getId(),
                trip.getDestination(),
                days.stream().map(this::toDayResponse).toList()
        );
    }

    public ItineraryResponse.ItineraryDayResponse toDayResponse(ItineraryDay day) {
        return new ItineraryResponse.ItineraryDayResponse(
                day.getDayNumber(),
                day.getTitle(),
                day.getSummary(),
                day.getWalkKm(),
                day.getStops().size(),
                day.getStops().stream().map(this::toStopResponse).toList(),
                toTimeline(day)
        );
    }

    private ItineraryResponse.ItineraryStopResponse toStopResponse(ItineraryStop stop) {
        return new ItineraryResponse.ItineraryStopResponse(
                stop.getId(),
                stop.getStopOrder(),
                stop.getTitle(),
                stop.getCategory(),
                stop.getTimeWindow(),
                stop.getNote(),
                stop.isOptionalStop(),
                stop.getLatitude(),
                stop.getLongitude()
        );
    }

    private List<ItineraryResponse.ItineraryTimelineItemResponse> toTimeline(ItineraryDay day) {
        List<ItineraryStop> stops = day.getStops();
        List<ItineraryResponse.ItineraryTimelineItemResponse> timeline = new ArrayList<>();
        if (stops.isEmpty()) {
            return timeline;
        }

        int cursor = parseTime(stops.get(0).getTimeWindow(), startMinuteFor(day));
        for (int index = 0; index < stops.size(); index++) {
            ItineraryStop stop = stops.get(index);
            int visitDuration = visitDurationMinutes(stop);
            int stopStart = cursor;
            int stopEnd = stopStart + visitDuration;
            ConstraintCheck constraint = validateOpeningHours(stop, stopStart, stopEnd);

            timeline.add(new ItineraryResponse.ItineraryTimelineItemResponse(
                    stop.getId(),
                    "STOP",
                    stop.getTitle(),
                    formatTime(stopStart),
                    formatTime(stopEnd),
                    visitDuration,
                    null,
                    null,
                    null,
                    stop.getCategory(),
                    stop.getNote(),
                    constraint.status(),
                    constraint.warning()
            ));

            if (index < stops.size() - 1) {
                ItineraryStop next = stops.get(index + 1);
                double distanceKm = distanceKm(stop, next);
                int travelMinutes = travelMinutes(distanceKm);
                int travelStart = stopEnd;
                int travelEnd = travelStart + travelMinutes;

                timeline.add(new ItineraryResponse.ItineraryTimelineItemResponse(
                        "travel_" + stop.getId() + "_" + next.getId(),
                        "TRAVEL",
                        travelMinutes + " min walk",
                        formatTime(travelStart),
                        formatTime(travelEnd),
                        travelMinutes,
                        round(distanceKm),
                        stop.getId(),
                        next.getId(),
                        "WALKING",
                        "Walking transfer between itinerary stops.",
                        "OK",
                        null
                ));
                cursor = roundUpToFive(travelEnd + bufferMinutes(day));
            }
        }
        return timeline;
    }

    private int startMinuteFor(ItineraryDay day) {
        String pace = day.getTrip() == null || day.getTrip().getPace() == null ? "BALANCED" : day.getTrip().getPace().name();
        return switch (pace) {
            case "RELAXED" -> 10 * 60;
            case "FULL" -> 9 * 60;
            default -> 9 * 60 + 30;
        };
    }

    private int bufferMinutes(ItineraryDay day) {
        String pace = day.getTrip() == null || day.getTrip().getPace() == null ? "BALANCED" : day.getTrip().getPace().name();
        return pace.equals("RELAXED") ? 8 : 3;
    }

    private int visitDurationMinutes(ItineraryStop stop) {
        String category = normalizeCategory(stop.getCategory());
        if (category.contains("FOOD")) {
            return 90;
        }
        if (category.contains("COFFEE")) {
            return 45;
        }
        if (category.contains("CULTURE")) {
            return 120;
        }
        return 60;
    }

    private ConstraintCheck validateOpeningHours(ItineraryStop stop, int startMinute, int endMinute) {
        OpeningWindow opening = openingWindowFor(stop);
        if (opening == null) {
            return new ConstraintCheck("OK", null);
        }
        if (startMinute < opening.openMinute()) {
            return new ConstraintCheck(
                    "WARNING",
                    stop.getTitle() + " may not be open yet. Opens around " + formatTime(opening.openMinute()) + "."
            );
        }
        if (endMinute > opening.closeMinute()) {
            return new ConstraintCheck(
                    "WARNING",
                    stop.getTitle() + " may not have enough time before closing at " + formatTime(opening.closeMinute()) + "."
            );
        }
        return new ConstraintCheck("OK", null);
    }

    private OpeningWindow openingWindowFor(ItineraryStop stop) {
        String category = normalizeCategory(stop.getCategory());
        if (category.contains("COFFEE")) {
            return new OpeningWindow(8 * 60, 18 * 60);
        }
        if (category.contains("FOOD")) {
            return new OpeningWindow(12 * 60, 22 * 60 + 30);
        }
        if (category.contains("CULTURE")) {
            return new OpeningWindow(10 * 60, 18 * 60);
        }
        return null;
    }

    private int travelMinutes(double distanceKm) {
        double adjustedDistance = distanceKm * 1.25;
        return Math.max(5, (int) Math.ceil((adjustedDistance / 4.5) * 60));
    }

    private double distanceKm(ItineraryStop from, ItineraryStop to) {
        double earthRadiusKm = 6371.0;
        double latDelta = Math.toRadians(to.getLatitude() - from.getLatitude());
        double lonDelta = Math.toRadians(to.getLongitude() - from.getLongitude());
        double fromLat = Math.toRadians(from.getLatitude());
        double toLat = Math.toRadians(to.getLatitude());
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(fromLat) * Math.cos(toLat)
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private int parseTime(String value, int fallback) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}")) {
            return fallback;
        }
        String[] parts = value.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour * 60 + minute;
    }

    private String formatTime(int minuteOfDay) {
        int normalized = Math.max(0, minuteOfDay);
        int hour = normalized / 60;
        int minute = normalized % 60;
        return "%02d:%02d".formatted(hour, minute);
    }

    private int roundUpToFive(int value) {
        return ((value + 4) / 5) * 5;
    }

    private String normalizeCategory(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record OpeningWindow(int openMinute, int closeMinute) {
    }

    private record ConstraintCheck(String status, String warning) {
    }
}
