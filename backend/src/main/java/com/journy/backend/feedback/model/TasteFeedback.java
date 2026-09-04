package com.journy.backend.feedback.model;

import jakarta.persistence.UniqueConstraint;

import com.journy.backend.user.model.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "taste_feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_feedback_user_event", columnNames = {"user_id", "event_key"}))
@Getter
@Setter
@NoArgsConstructor
public class TasteFeedback {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    private String placeId;

    @Column(nullable = false)
    private String placeName;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TasteFeedbackAction action;

    @Column(nullable = false)
    private int weight;

    private String reason;

    // Null on historical rows: their mixed identity has not been reverified.
    private Boolean identityVerified;
    private String source;
    private String contextId;
    @Column(name = "event_key")
    private String eventKey;

    @Column(nullable = false)
    private Instant createdAt;

    public TasteFeedback(
            UserAccount user,
            String placeId,
            String placeName,
            String category,
            TasteFeedbackAction action,
            int weight,
            String reason
    ) {
        this.id = "tfb_" + UUID.randomUUID();
        this.user = user;
        this.placeId = placeId;
        this.placeName = placeName;
        this.category = category;
        this.action = action;
        this.weight = weight;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
}
