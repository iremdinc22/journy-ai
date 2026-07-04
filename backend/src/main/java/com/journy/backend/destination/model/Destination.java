package com.journy.backend.destination.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "destinations")
@Getter
@Setter
@NoArgsConstructor
public class Destination {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false, length = 800)
    private String description;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String tags;

    @Column(nullable = false)
    private String bestFor;

    @Column(nullable = false)
    private int placeCount;

    @Column(nullable = false)
    private double averageDailyWalkKm;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false)
    private boolean popular;

    public Destination(
            String name,
            String country,
            String description,
            String imageUrl,
            String tags,
            String bestFor,
            int placeCount,
            double averageDailyWalkKm,
            boolean available,
            boolean popular
    ) {
        this.id = "dst_" + UUID.randomUUID();
        this.name = name;
        this.country = country;
        this.description = description;
        this.imageUrl = imageUrl;
        this.tags = tags;
        this.bestFor = bestFor;
        this.placeCount = placeCount;
        this.averageDailyWalkKm = averageDailyWalkKm;
        this.available = available;
        this.popular = popular;
    }
}
