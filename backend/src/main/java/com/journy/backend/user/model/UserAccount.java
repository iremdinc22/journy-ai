package com.journy.backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {
    @Id
    private String id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String travelStyle;

    @Column
    private String defaultPace;

    @Column
    private String defaultBudget;

    @Column
    private String foodDiscovery;

    @Column
    private boolean planChangeNotifications;

    @Column
    private boolean foodWindowNotifications;

    @Column(nullable = false)
    private Instant createdAt;

    public UserAccount(String fullName, String email, String passwordHash, String travelStyle) {
        this.id = "usr_" + UUID.randomUUID();
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.travelStyle = travelStyle;
        this.defaultPace = "BALANCED";
        this.defaultBudget = "BALANCED";
        this.foodDiscovery = "LOCAL_FIRST";
        this.planChangeNotifications = true;
        this.foodWindowNotifications = true;
        this.createdAt = Instant.now();
    }
}
