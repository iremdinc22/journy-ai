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

    private String address;

    private Double latitude;

    private Double longitude;

    private String openingHours;

    private Integer estimatedVisitMinutes;

    private String tags;

    public Place(String name, String city, PlaceCategory category, String description, String priceLevel, double rating, String imageUrl) {
        this.id = "plc_" + UUID.randomUUID();
        this.name = name;
        this.city = city;
        this.category = category;
        this.description = description;
        this.priceLevel = priceLevel;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.address = city + " city center";
        this.latitude = defaultLatitude(city);
        this.longitude = defaultLongitude(city);
        this.openingHours = defaultOpeningHours(category);
        this.estimatedVisitMinutes = defaultDuration(category);
        this.tags = category.name().toLowerCase() + ",walkable";
    }

    public Place(
            String name,
            String city,
            PlaceCategory category,
            String description,
            String priceLevel,
            double rating,
            String imageUrl,
            String address,
            double latitude,
            double longitude,
            String openingHours,
            int estimatedVisitMinutes,
            String tags
    ) {
        this(name, city, category, description, priceLevel, rating, imageUrl);
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openingHours = openingHours;
        this.estimatedVisitMinutes = estimatedVisitMinutes;
        this.tags = tags;
    }

    private double defaultLatitude(String city) {
        return switch (city.toLowerCase()) {
            case "paris" -> 48.8566;
            case "rome" -> 41.9028;
            case "barcelona" -> 41.3874;
            default -> 52.3676;
        };
    }

    private double defaultLongitude(String city) {
        return switch (city.toLowerCase()) {
            case "paris" -> 2.3522;
            case "rome" -> 12.4964;
            case "barcelona" -> 2.1686;
            default -> 4.9041;
        };
    }

    private String defaultOpeningHours(PlaceCategory category) {
        return switch (category) {
            case COFFEE -> "08:00 - 18:00";
            case FOOD -> "12:00 - 22:30";
            case CULTURE -> "10:00 - 18:00";
            default -> "Open route window";
        };
    }

    private int defaultDuration(PlaceCategory category) {
        return switch (category) {
            case COFFEE -> 45;
            case FOOD -> 90;
            case CULTURE -> 120;
            case WALKING -> 75;
            default -> 60;
        };
    }
}
