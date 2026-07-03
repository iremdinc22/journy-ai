package com.journy.backend.notification.model;

import com.journy.backend.user.model.UserAccount;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class AppNotification {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private boolean unread;

    @Column(nullable = false)
    private Instant createdAt;

    public AppNotification(UserAccount user, String type, String title, String message, boolean unread, Instant createdAt) {
        this.id = "ntf_" + UUID.randomUUID();
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.unread = unread;
        this.createdAt = createdAt;
    }
}
