package com.cricketplex.service;

import com.cricketplex.entity.Notification;
import com.cricketplex.entity.User;
import com.cricketplex.repository.NotificationRepository;
import com.cricketplex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Create and immediately push a notification to the user via WebSocket.
     *
     * @param userId  target user's UUID
     * @param type    e.g. CHALLENGE_RECEIVED, CHALLENGE_ACCEPTED, CHALLENGE_DECLINED, ADDED_TO_GROUP
     * @param title   short heading
     * @param body    full descriptive text
     * @param link    optional deep-link (can be null)
     */
    @Transactional
    public Notification create(UUID userId, String type, String title, String body, String link) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return null;

        Notification n = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .link(link)
                .read(false)
                .build();
        n = notificationRepo.save(n);

        // Push via WebSocket so the bell updates in real-time
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, toDto(n));
        return n;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecent(UUID userId) {
        return notificationRepo
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) {
                n.setRead(true);
                notificationRepo.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepo.markAllReadByUserId(userId);
    }

    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("link", n.getLink());
        m.put("read", n.isRead());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }
}
