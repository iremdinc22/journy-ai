package com.journy.backend.notification.service;

import com.journy.backend.notification.dto.NotificationResponse;
import com.journy.backend.notification.mapper.NotificationMapper;
import com.journy.backend.notification.repository.AppNotificationRepository;
import com.journy.backend.security.CurrentUserService;
import com.journy.backend.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {
    private final AppNotificationRepository appNotificationRepository;
    private final NotificationMapper notificationMapper;
    private final CurrentUserService currentUserService;

    public NotificationService(
            AppNotificationRepository appNotificationRepository,
            NotificationMapper notificationMapper,
            CurrentUserService currentUserService
    ) {
        this.appNotificationRepository = appNotificationRepository;
        this.notificationMapper = notificationMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> notifications() {
        UserAccount user = currentUserService.currentUser();
        return appNotificationRepository.findTop20ByUserEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail())
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
}
