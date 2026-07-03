package com.journy.backend.notification.mapper;

import com.journy.backend.notification.dto.NotificationResponse;
import com.journy.backend.notification.model.AppNotification;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationMapper {
    public NotificationResponse toResponse(AppNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isUnread(),
                relativeTime(notification.getCreatedAt())
        );
    }

    private String relativeTime(Instant createdAt) {
        Duration age = Duration.between(createdAt, Instant.now());
        if (age.toHours() < 24) {
            return "Today";
        }
        if (age.toDays() == 1) {
            return "Yesterday";
        }
        return age.toDays() + " days ago";
    }
}
