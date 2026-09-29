package com.buildup.notification.service;

import com.buildup.common.exception.ApiException;
import com.buildup.notification.dto.NotificationResponse;
import com.buildup.notification.entity.Notification;
import com.buildup.notification.repository.NotificationRepository;
import com.buildup.site.entity.Site;
import com.buildup.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification create(
            User user,
            Site site,
            String type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ) {
        return notificationRepository.save(Notification.create(
                user,
                site,
                type,
                title,
                message,
                referenceType,
                referenceId
        ));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User user) {
        return notificationRepository
                .findTop100ByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadAtIsNull(user.getId());
    }

    @Transactional
    public NotificationResponse markRead(User user, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "Notification was not found"
                ));
        notification.markRead(databaseTimestamp());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(User user) {
        return notificationRepository.markAllRead(user.getId(), databaseTimestamp());
    }

    private LocalDateTime databaseTimestamp() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
