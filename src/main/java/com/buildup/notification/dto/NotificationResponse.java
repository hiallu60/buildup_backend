package com.buildup.notification.dto;

import com.buildup.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String referenceType,
        Long referenceId,
        Long siteId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getSite() == null ? null : notification.getSite().getId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
