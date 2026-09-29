package com.buildup.notification.controller;

import com.buildup.notification.dto.NotificationResponse;
import com.buildup.notification.dto.ReadAllResponse;
import com.buildup.notification.dto.UnreadCountResponse;
import com.buildup.notification.service.NotificationService;
import com.buildup.user.entity.User;
import com.buildup.user.service.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public NotificationController(
            CurrentUserService currentUserService,
            NotificationService notificationService
    ) {
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(currentUserService.require(jwt));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(
                notificationService.unreadCount(currentUserService.require(jwt))
        );
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId
    ) {
        User user = currentUserService.require(jwt);
        return notificationService.markRead(user, notificationId);
    }

    @PatchMapping("/read-all")
    public ReadAllResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return new ReadAllResponse(
                notificationService.markAllRead(currentUserService.require(jwt))
        );
    }
}
