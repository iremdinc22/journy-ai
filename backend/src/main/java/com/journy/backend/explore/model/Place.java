package com.journy.backend.explore.model;

import com.journy.backend.place.enums.PlaceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "places")
@Getter
@Setter
@NoArgsConstructor
public class Place {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlaceCategory category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private String priceLevel;

    @Column(nullable = false)
    private double rating;

    private String imageUrl;

    public Place(String name, String city, PlaceCategory category, String description, String priceLevel, double rating, String imageUrl) {
        this.id = "plc_" + UUID.randomUUID();
        this.name = name;
        this.city = city;
        this.category = category;
        this.description = description;
        this.priceLevel = priceLevel;
        this.rating = rating;
        this.imageUrl = imageUrl;
    }
}
