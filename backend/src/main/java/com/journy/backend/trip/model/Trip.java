package com.journy.backend.trip.model;

import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.user.model.UserAccount;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class Trip {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String destination;

    @Column
    private String startingArea;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TravelerType travelerType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BudgetMode budget;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TripPace pace;

    @ElementCollection
    @CollectionTable(name = "trip_interests", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "sort_order")
    @Enumerated(EnumType.STRING)
    @Column(name = "interest", nullable = false)
    private List<TravelInterest> interests = new ArrayList<>();

    @Column(nullable = false)
    private int totalStops;

    @Column(nullable = false)
    private int foodPicks;

    @Column(nullable = false)
    private double averageWalkKm;

    @Column(nullable = false)
    private boolean currentTrip;

    @Column(nullable = false)
    private Instant createdAt;

    public Trip(
            UserAccount user,
            String destination,
            String startingArea,
            LocalDate startDate,
            LocalDate endDate,
            TravelerType travelerType,
            BudgetMode budget,
            TripPace pace,
            Set<TravelInterest> interests
    ) {
        this.id = "trip_" + UUID.randomUUID();
        this.user = user;
        this.destination = destination;
        this.startingArea = startingArea;
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelerType = travelerType;
        this.budget = budget;
        this.pace = pace;
        this.interests = new ArrayList<>(interests);
        this.createdAt = Instant.now();
    }

    public int dayCount() {
        return Math.max(1, (int) ChronoUnit.DAYS.between(startDate, endDate));
    }
}
