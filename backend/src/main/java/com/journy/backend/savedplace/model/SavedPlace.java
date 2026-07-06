package com.journy.backend.savedplace.model;

import com.journy.backend.user.model.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "saved_places",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_place_user_place", columnNames = {"user_id", "place_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class SavedPlace {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "place_id", nullable = false)
    private String placeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 600)
    private String description;

    @Column(nullable = false)
    private String priceLevel;

    @Column(nullable = false)
    private double rating;

    @Column(nullable = false)
    private String imageUrl;

    private String address;

    private String openingHours;

    private Integer estimatedVisitMinutes;

    private String tags;

    @Column(nullable = false)
    private Instant createdAt;

    public SavedPlace(
            UserAccount user,
            String placeId,
            String name,
            String city,
            String category,
            String description,
            String priceLevel,
            double rating,
            String imageUrl,
            String address,
            String openingHours,
            Integer estimatedVisitMinutes,
            String tags
    ) {
        this.id = "svp_" + UUID.randomUUID();
        this.user = user;
        this.placeId = placeId;
        this.name = name;
        this.city = city;
        this.category = category;
        this.description = description;
        this.priceLevel = priceLevel;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.address = address;
        this.openingHours = openingHours;
        this.estimatedVisitMinutes = estimatedVisitMinutes;
        this.tags = tags;
        this.createdAt = Instant.now();
    }
}
