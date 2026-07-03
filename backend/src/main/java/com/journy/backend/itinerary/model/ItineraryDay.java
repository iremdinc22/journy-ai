package com.journy.backend.itinerary.model;

import com.journy.backend.trip.model.Trip;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "itinerary_days")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryDay {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private double walkKm;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    private List<ItineraryStop> stops = new ArrayList<>();

    public ItineraryDay(Trip trip, int dayNumber, String title, String summary, double walkKm) {
        this.id = "day_" + UUID.randomUUID();
        this.trip = trip;
        this.dayNumber = dayNumber;
        this.title = title;
        this.summary = summary;
        this.walkKm = walkKm;
    }

    public void addStop(ItineraryStop stop) {
        stops.add(stop);
        stop.setDay(this);
    }
}
