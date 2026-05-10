package com.cricketplex.controller;

import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Last 30 notifications for the current user */
    @GetMapping
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.getRecent(principal.getId()));
    }

    /** Unread count — polled by the bell on a schedule */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(principal.getId())));
    }

    /** Mark a single notification as read */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        notificationService.markRead(id, principal.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Mark all notifications as read */
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
