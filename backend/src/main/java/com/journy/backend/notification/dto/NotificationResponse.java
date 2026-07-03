package com.journy.backend.notification.dto;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        boolean unread,
        String createdAt
) {
}
