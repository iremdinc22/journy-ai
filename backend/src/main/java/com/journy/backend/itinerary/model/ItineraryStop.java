package com.journy.backend.itinerary.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "itinerary_stops")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryStop {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    @Column(nullable = false)
    private int stopOrder;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String timeWindow;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public ItineraryStop(
            int stopOrder,
            String title,
            String category,
            String timeWindow,
            String note,
            double latitude,
            double longitude
    ) {
        this.id = "stop_" + UUID.randomUUID();
        this.stopOrder = stopOrder;
        this.title = title;
        this.category = category;
        this.timeWindow = timeWindow;
        this.note = note;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
