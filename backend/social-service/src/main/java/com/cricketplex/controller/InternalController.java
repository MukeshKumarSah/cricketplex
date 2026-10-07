package com.cricketplex.controller;

import com.cricketplex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.internal.api-key:cricketplex-internal}")
    private String internalApiKey;

    @PostMapping("/notifications")
    public ResponseEntity<Void> createNotification(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!internalApiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        notificationService.create(
                UUID.fromString(body.get("userId")),
                body.get("type"),
                body.get("title"),
                body.get("body"),
                body.get("link")
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer-events")
    public ResponseEntity<Void> transferEvent(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestBody Map<String, Object> body) {
        if (!internalApiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        messagingTemplate.convertAndSend("/topic/transfer", body);
        return ResponseEntity.ok().build();
    }
}
