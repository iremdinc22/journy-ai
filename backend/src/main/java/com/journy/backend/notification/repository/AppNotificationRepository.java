package com.journy.backend.notification.repository;

import com.journy.backend.notification.model.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, String> {
    List<AppNotification> findTop20ByOrderByCreatedAtDesc();

    List<AppNotification> findTop20ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
